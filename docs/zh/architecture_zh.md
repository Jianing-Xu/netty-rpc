# 架构与设计文档

[English Document](../en/architecture.md)

## 1. 项目概述

`netty-rpc` 是一个基于 Java 17、Netty 4.1.x 与 ZooKeeper 构建的工程级 RPC 框架。项目采用最新的 **微内核架构（核心 SPI + 扩展插件）** 和 **TDD 驱动开发**，覆盖了 RPC 框架的核心组成部分：通信协议、编解码、服务注册发现、代理调用、负载均衡、超时容错。

---

## 2. 技术选型说明

| 技术栈 | 选型理由 |
|-------|--------|
| **Java 17** | LTS 版本，具备更好的 GC 表现、sealed classes、pattern matching 等语言特性 |
| **Netty 4.1.x** | 高性能异步事件驱动网络框架，提供完整的 ByteBuf 管理、ChannelPipeline 责任链、主从 Reactor 线程模型 |
| **Apache Curator 5.5.0** | ZooKeeper 的工业级客户端，封装了重试策略、Session 管理、节点操作等复杂逻辑 |
| **JDK 原生序列化** | 满足"不引入未声明依赖"的约束。通过 `Serializer` 接口抽象，可随时切换为 Protobuf/Kryo |
| **JUnit 5 + Mockito** | 现代化测试框架组合，支持参数化测试、生命周期回调、Mock 注入 |
| **JaCoCo 0.8.11** | Maven 原生集成的覆盖率工具，支持 `check` goal 强制执行覆盖率阈值 |

**明确不使用的技术：** Spring/Spring Boot、TLS/安全认证、流式 RPC、多语言 SDK。

---

## 3. 架构设计

### 3.1 微内核组件架构（Microkernel + SPI）

```text
┌─────────────────────────────────────────────────────────────────┐
│                    netty-rpc-core (Core Engine)                 │
│  职责：引导程序、代理生成、传输协调、Future 映射、连接管理                │
│                                                                 │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐          │
│  │RpcBootstrap │ │ NettyClient  │ │  RpcServer        │         │
│  │(注解扫包暴露) │ │ (TCP 连接管理)│ │  (服务端监听启动)     │         │
│  └──────┬──────┘ └──────┬───────┘ └───────┬──────────┘          │
│         │               │                 │                     │
│  ┌──────┴──────┐ ┌──────┴───────┐ ┌───────┴──────────┐         │
│  │PendingReqs  │ │RpcClientProxy│ │RpcServerHandler   │        │
│  │ (Future交互) │ │ (动态拦截组装) │ │ (反射+线程池处理)   │        │
│  └─────────────┘ └──────────────┘ └──────────────────┘          │
├─────────────────────────────────────────────────────────────────┤
│                   netty-rpc-api (SPI 扩展点规范)                 │
│  职责：纯技术接口契约，与具体实现框架解耦（SPI Base）                  │
│                                                                 │
│  ┌─────────────┐ ┌────────────────┐ ┌──────────────────┐      │
│  │ Serializer   │ │ LoadBalancer   │ │ServiceRegistry    │      │
│  │ (序列化接口)   │ │ (负载均衡接口)   │ │ServiceDiscovery   │      │
│  └─────────────┘ └────────────────┘ └──────────────────┘      │
├─────────────────────────────────────────────────────────────────┤
│                   插件实现层 (Plugin Implementations)            │
│  职责：对 API 的具体技术实现，可自由插拔和替换                        │
│                                                                 │
│  [netty-rpc-registry-zk]     -> 基于 Curator + ZooKeeper 的注册实现 │
│  [netty-rpc-core 的内置实现]  -> Netty 编解码器、Protostuff 序列化    │
├─────────────────────────────────────────────────────────────────┤
│                      netty-rpc-common                           │
│  职责：基础公共模型、全局异常定义、网络共享常量，全模块依赖。               │
│                                                                 │
│  Models:  RpcRequest, RpcResponse, RpcMessage, RpcProtocolHeader│
│  Enums:   MessageType, SerializationType, ProtocolConstants     │
│  Annots:  @RpcService, @RpcReference                            │
│  Except:  RpcException                                          │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 客户端架构

```
业务代码 ──▶ RpcClientProxy (JDK Proxy)
              │
              ├─ 1. 构建 RpcRequest (requestId, className, method, args)
              ├─ 2. ServiceDiscovery.discover() → 地址列表
              ├─ 3. LoadBalancer.select() → 目标 host:port
              ├─ 4. NettyClient.sendRequest() → CompletableFuture
              │        │
              │        ├─ PendingRequests.put(requestId) 注册 Future
              │        ├─ RpcMessage.buildRequest() 封装协议消息
              │        └─ Channel.writeAndFlush(msg) Netty 发送
              │
              └─ 5. future.get(timeout) 同步阻塞等待结果
```

### 3.3 服务端架构

```
Netty Bootstrap (Boss=1, Worker=CPU*2)
    │
    ├─ RpcDecoder     ← 解码二进制帧为 RpcMessage
    ├─ RpcEncoder     ← 编码 RpcMessage 为二进制帧
    └─ RpcServerHandler
         │
         ├─ 提取 RpcRequest from RpcMessage
         ├─ 提交到 业务线程池 (防止阻塞 NIO Worker 线程)
         ├─ serviceMap.get(className) 查找服务实例
         ├─ Method.invoke() 反射调用
         ├─ 构建 RpcResponse (success/error)
         └─ ctx.writeAndFlush(RpcMessage.buildResponse())
```

### 3.4 注册中心交互模型

```
ZooKeeper 节点结构:

/netty-rpc
  └── /services
        ├── /com.example.HelloService        ← PERSISTENT
        │     ├── /127.0.0.1:8080            ← EPHEMERAL
        │     └── /127.0.0.1:8081            ← EPHEMERAL
        └── /com.example.UserService         ← PERSISTENT
              └── /192.168.1.10:9090         ← EPHEMERAL
```

- **服务注册**：Server 启动后通过 `ZkServiceRegistry.register()` 在服务路径下创建临时子节点
- **服务发现**：Client 通过 `ZkServiceDiscovery.discover()` 执行 `getChildren()` 获取地址列表
- **自动摘除**：Provider 宕机引发 ZK Session 超时，临时节点自动删除

---

## 4. 通信协议设计

### 4.1 帧结构

```
+--------+--------+--------+--------+--------+--------+----------+---------+---------+---------+
| Magic  |Version | MsgType| SerType| Status |      RequestId     | BodyLen |  CRC32  |  Body   |
| 2 Byte | 1 Byte | 1 Byte | 1 Byte | 1 Byte |     8 Bytes       | 4 Bytes | 4 Bytes | N Bytes |
+--------+--------+--------+--------+--------+--------------------+---------+---------+---------+
|<--------------------- Header (22 Bytes) ----------------------------------------->|< Body  >|
```

### 4.2 字段定义

| 字段 | 字节数 | 类型 | 说明 |
|------|-------|------|------|
| Magic | 2 | short | 魔数 `0xCAFE`，用于过滤非法请求 |
| Version | 1 | byte | 协议版本号（当前为 1），保证向后兼容 |
| MsgType | 1 | byte | 0=Request, 1=Response, 2=Heartbeat |
| SerType | 1 | byte | 序列化类型（0=JDK, 1=Protobuf），预留扩展位 |
| Status | 1 | byte | 响应状态：0=成功, 1=异常 |
| RequestId | 8 | long | 请求唯一标识，用于关联请求与响应 |
| BodyLen | 4 | int | 消息体字节长度 |
| CRC32 | 4 | int | 消息体(Body)数据的 CRC32 校验和，用于完整性校验 |
| Body | N | bytes | 序列化后的 `RpcRequest` 或 `RpcResponse` |

### 4.3 粘包/拆包处理

`RpcDecoder` 通过以下机制解决 TCP 粘包/拆包：

1. **头部长度校验**：若可读字节 < 22（HEADER_LENGTH），直接返回等待更多数据
2. **标记回退**：使用 `markReaderIndex()` 标记当前位置，头部读取后若 Body 不完整则 `resetReaderIndex()` 回退
3. **魔数校验**：读取 Magic 后验证是否为 `0xCAFE`，非法数据立即抛出异常

---

## 5. 并发与线程模型

### 5.1 Netty 线程模型

```
Server 端：
  BossGroup(1 thread)     ← 处理 Accept 事件
  WorkerGroup(CPU*2)      ← 处理 Read/Write I/O

Client 端：
  EventLoopGroup(4 threads) ← 所有 Channel 共用
```

### 5.2 业务线程池隔离

`RpcServerHandler` 将请求派发到独立的业务线程池 `ExecutorService`：

```java
bizThreadPool.submit(() -> {
    Object result = invokeService(request);   // 可能阻塞
    ctx.writeAndFlush(responseMsg);
});
```

**隔离原因**：反射调用的目标方法可能存在 I/O 阻塞（如数据库查询）。若在 Netty Worker 线程中执行，会挂死整个 EventLoop 下所有 Channel 的网络 I/O。

### 5.3 异步 Future 映射

`PendingRequests` 使用 `ConcurrentHashMap<Long, CompletableFuture<RpcResponse>>` 实现无锁的请求-响应关联：

```
发送请求 → put(requestId, future)        [Netty Worker 线程]
收到响应 → remove(requestId) + complete  [Netty Worker 线程]
业务等待 → future.get(timeout)           [业务调用线程]
```

---

## 6. 容错与超时设计

| 场景 | 处理策略 |
|------|---------|
| **网络超时** | `CompletableFuture.get(timeoutMs, MILLISECONDS)` 抛出 `TimeoutException` |
| **连接失败** | `Bootstrap.connect()` 设置 `CONNECT_TIMEOUT_MILLIS=5000` |
| **服务端异常** | 异常填入 `RpcResponse.error`，Client 端 Proxy 解封装后抛出 `RpcException` |
| **服务不存在** | 反射失败时返回 error response；注册中心查不到时直接抛异常 |
| **Provider 宕机** | ZK 临时节点自动摘除，Client 下次 discover 不再返回该地址 |
| **Channel 断连** | `closeFuture` 监听自动移除缓存；`writeAndFlush` 失败时 completeExceptionally |

---

## 7. 负载均衡策略

| 策略 | 类名 | 算法 |
|------|------|------|
| Random | `RandomLoadBalancer` | `ThreadLocalRandom.nextInt(size)` |
| RoundRobin | `RoundRobinLoadBalancer` | `AtomicInteger` 原子自增取模，溢出安全 (`& Integer.MAX_VALUE`) |
| ConsistentHash | `ConsistentHashLoadBalancer` | 基于虚拟节点 (160个副本) 和 FNV1a_32 算法建立哈希环 |

以上均实现 `LoadBalancer` 接口，通过构造注入或 Bootstrap 设置替换策略。
