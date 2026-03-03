# 核心逻辑深度解析

[English Document](../en/core_logic.md)

本文档专注于剖析 Netty-RPC 的核心流转机制。作为一款生产级别的高性能 RPC 框架，我们将重点探讨客户端的动态代理调度、服务端的异步非阻塞执行、全链路的响应式 Future 映射机制，以及服务端的令牌限流模型。

---

## 1. 核心链路全局架构

首先，我们通过一幅全景图回顾一次完整的 RPC 请求是如何从调用方发起，穿越网络代理，到达服务端业务线程池，最终异/同步返回结果的整个宏观链路。

```mermaid
sequenceDiagram
    participant User as Consumer (业务代码)
    participant Proxy as RpcClientProxy (动态代理)
    participant Discovery as ZkServiceDiscovery
    participant LB as LoadBalancer
    participant Client as NettyClient
    participant Server as RpcServer (Netty Boss/Worker)
    participant Handler as RpcServerHandler
    participant RateLimiter as TokenBucketRateLimiter
    participant BizPool as BizThreadPool
    participant Provider as Provider (业务代码)

    User->>Proxy: 1. 调用接口方法 (同步/CompletableFuture)
    Proxy->>Discovery: 2. 获取服务地址列表 /services/{ClassName}
    Discovery-->>Proxy: 返回 [ip1:port1, ip2:port2]
    Proxy->>LB: 3. 执行负载均衡 (RoundRobin/Random/Hash)
    LB-->>Proxy: 选中目标节点 (Target Host)
    
    Proxy->>Client: 4. 封装 RpcRequest，发起网络调用
    Client-->>Client: 4.1 将 RequestID 与 Future 绑定，放入 PendingRequests
    Client->>Server: 4.2 管道流转，触发 Channel.writeAndFlush (Protobuf 编码)
    
    Note over Server,Handler: TCP 网络传输
    
    Server->>Handler: 5. 接收报文，Protobuf 反序列化完毕，激活 channelRead0
    Handler->>RateLimiter: 6. 申请令牌 (tryAcquire)
    alt 限流超负荷
        RateLimiter-->>Handler: False
        Handler-->>Client: 抛出 RpcException(Server Busy) 快速失败
    else 正常通过
        RateLimiter-->>Handler: True
        Handler->>BizPool: 7. 提交任务到独立业务线程池 (防御阻塞 NIO Worker)
        BizPool->>Provider: 8. 反射调用本地 Bean 实例
        
        alt 目标方法返回 CompletableFuture (真异步)
            Provider-->>BizPool: 返回未完成的 Future 句柄
            BizPool-->>Handler: 挂起当前线程，解除占用
            Provider->>Provider: 异步执行耗时 IO
            Provider-->>Handler: 原生 Future.complete() 触发
        else 目标方法返回普通对象 (同步执行)
            Provider-->>BizPool: 阻塞计算后返回实际结果
            BizPool-->>Handler: 直接装配结果
        end
        
        Handler->>Client: 9. 构建 RpcResponse，执行 netty writeAndFlush 回传
    end
    
    Client-->>Client: 10. 解析 Response，取出 RequestID
    Client-->>Client: 从 PendingRequests 取出绑定的 Future 并调用 complete()
    
    alt 用户调用的原接口是异步方法
        Proxy-->>User: 11. 立即返回 CompletableFuture (非阻塞)
        Client-->>User: 网络报文到达后，异步触发回调 (thenAccept)
    else 用户调用的原接口是普通方法
        Proxy-->>User: 11. 代理内部阻塞等待 Future.get(timeout)，返回最终结果
    end
```

---

## 2. 核心源码级流程拆解

### 2.1 动态代理与重试调度 (`RpcClientProxy`)

当我们编写类似于 `@RpcReference(retries = 3)` 的注入代码后，Spring 实则会将一个基于 `Proxy.newProxyInstance()` 动态生成的假对象赋予该字段。

每次调用该对象的方法，都会经过以下逻辑拦截：

1. **方法类型探针**：第一时间识别被调用方法的返回类型。`boolean isAsync = CompletableFuture.class.isAssignableFrom(method.getReturnType());`
2. **容错机制包裹**：进入 `while(tryCount < maxTries)` 重试循环池。这确保了因瞬时网络抖动引发的 `RpcException` 不会直接击穿用户逻辑，而在框架底层默默自愈。
3. **请求投递 (Async)**：如果是异步执行，代理不调用 `Future.get(timeout)`，而通过 `whenComplete` 编排后续发生错误时引发的重试跳转（即通过递归 `sendAsyncWithRetry` 并桥接最初那把 Future 钥匙）。
4. **请求投递 (Sync)**：对于传统同步接口，代理通过 `get(timeoutMs, TimeUnit.MILLISECONDS)` 化身"阻塞网关"，使得用户感受不到这段跨越公网的远程旅行。

### 2.2 Zookeeper 的元数据驱动注册与路由

组件间的元数据寻址通过 `Curator` API 进行强一致性的 ZNode 交互：

```mermaid
graph TD
    A[Provider服务启动] -->|注册服务| B(/netty-rpc/services/接口名)
    B -->|建立临时节点 EPHEMERAL| C[IP1:Port1]
    B -->|建立临时节点 EPHEMERAL| D[IP2:Port2]
    
    E[Consumer客户端访问代理] -->|查询接口列表发现| B
    B -->|拉取所有孩子| E
    
    F((ZK Session 超时))-.->|宕机触发| C
    F -->|删除孤儿临时节点| B
    
    E -->|第二次查询| B
    B -->|仅返回存活| D
```
服务端注册时，先保证父节点（按接口全限定名命名）作为**持久节点 (PERSISTENT)**存在。随后在父节点下方创建以当前节点 IP和监听端口为名额的**瞬时节点 (EPHEMERAL)**。这种极为经典的设计，借助 ZK Session 维持心跳的特性，天然实现了服务端集群节点上下线的动态感知 (Health Checking)。

### 2.3 Reactor 线程护城河：业务线程分离策略 (`RpcServerHandler`)

如果所有网络请求的处理都在处理网络 I/O 事件的 Netty Worker 线程组 (通常等同于核心数*2) 内执行完毕，一旦遇到数据库死锁或远端 RPC 慢查询：

`业务代码阻塞 -> Worker线程卡死 -> 该Worker负责的上千个TCP连接全部假死无法读写网络报文 -> Netty 全面瘫痪`

为了解决这个"Reactor反模式"噩梦，服务器通道事件到达 `RpcServerHandler` 处理层时，我们制定了硬性规约：
```java
// 反射逻辑不再由 channelRead0 目前依赖的线程执行
bizThreadPool.submit(() -> {
    ...
    Object result = invokeService(request);
});
```
我们将业务反射投递给了全局独立的**业务线程池 (`bizThreadPool`)**。使得高并发下尽管耗时业务积压在线程池队列中，但是所有客户端维持心跳与发送小请求的网络链路均处于畅通无阻状态。

### 2.4 TokenBucket 高性能无锁限流器

为保护后端应用，我们引入了基于单节点令牌桶 (Token Bucket) 的 `RateLimiter` 扩展。

算法核心：以恒定速率 `rate` "匀速滴入"计算好的配额池。但在 Java 实现中，如果使用单独起一个定时任务每秒钟去加令牌由于调度毛刺成本太高，我们使用的是**惰性水滴计算流（Lazy Refill）**：
```java
private void refill() {
    long now = System.currentTimeMillis();
    if (now > lastRefillTimestamp) {
        long generatedTokens = (now - lastRefillTimestamp) * rate / 1000;
        if (generatedTokens > 0) {
            tokens = (int) Math.min(capacity, tokens + generatedTokens);
            lastRefillTimestamp = now;
        }
    }
}
```
当流量经过该请求入口时，系统会自动计算 "当前时间" 距离 "上一次产生配额时间" 的差值，瞬间乘上速率推导算出这期间本应下方的积攒令牌，无阻塞完成安全加算并直接扣减，极为高效、优雅。

### 2.5 异步调用：返回 CompletableFuture 的服务端调度

在限流器拦截与业务线程派发之间，存在本次引擎升级最具价值的响应式改造设计：

```mermaid
graph LR
    A[RpcServerHandler] -->|反射调用| B(HelloServiceImpl)
    B -->|返回| C{{"CompletableFuture.completedFuture(...)"}}
    C -->|instanceof 判定为 Future| D[注册 whenComplete 回调]
    D -.->|挂起并立即释放 bizThread| E(框架让出 CPU)
    C -->|由其它网络/中间件线程 Complete| F[触发 Netty writeAndFlush 回传包]
```

这构成了双端的响应式：
* 客户端无需等待返回，即可将网络结果钩子交由业务
* 业务服务端通过将 JDBC / Redis 客户端生成的 CompletableFuture 直接向外层返回（Return），无需在业务层执行任何阻塞代码即可被 `RpcServerHandler` 承接异步监听。从调用端到提供端实现了物理意义上 **无一阻塞点**。
