# 设计模式说明

## 1. 动态代理模式 (Proxy Pattern)

**使用位置**: `RpcClientProxy`

**解决的问题**: 使业务代码通过接口调用远程方法如同调用本地方法一般透明，屏蔽序列化、网络传输、服务发现等底层细节。

**实现方式**: JDK `java.lang.reflect.Proxy` 在运行时为任意接口生成代理实例。`InvocationHandler` 拦截每次方法调用，将其转化为 `RpcRequest` 并通过 `NettyClient` 发送。

```java
// 业务代码视角 —— 完全无感知底层 RPC 细节
HelloService service = proxy.create(HelloService.class);
String result = service.sayHello("World");  // 实际发起了网络请求
```

**为何适合**: RPC 框架的核心价值就在于"远程调用本地化"。动态代理是实现这一目标最自然的模式。

---

## 2. 策略模式 (Strategy Pattern)

**使用位置**: `LoadBalancer` 接口及其实现 `RandomLoadBalancer` / `RoundRobinLoadBalancer`

**解决的问题**: 从多个可用服务提供者中选择目标节点的算法可能随业务场景变化（如加权、一致性哈希）。

**实现方式**: 定义 `LoadBalancer` 接口，不同的选址算法作为独立实现类，通过构造注入替换。

```java
// 构造时选择策略
new RpcClientProxy(discovery, new RoundRobinLoadBalancer(), client, 5000);
// 切换策略无需修改任何其他代码
new RpcClientProxy(discovery, new RandomLoadBalancer(), client, 5000);
```

**为何适合**: 符合开闭原则，新增策略仅需实现接口，不修改已有代码。负载均衡是典型的"算法族"场景。

---

## 3. 责任链模式 (Chain of Responsibility)

**使用位置**: Netty `ChannelPipeline` 中的 Handler 链

**解决的问题**: 将网络数据的接收、解码、业务处理、编码、发送等步骤解耦为独立环节。

**实现方式**: Netty Pipeline 天然是责任链模式的体现：

```
Inbound:  ByteBuf → RpcDecoder → RpcServerHandler
Outbound: RpcMessage → RpcEncoder → ByteBuf
```

每个 Handler 只关注自己的职责（解码 / 业务 / 编码），数据在链条中依次流转。

**为何适合**: 网络协议处理天然是多阶段的。Pipeline 模式使得新增处理环节（如压缩、认证）只需插入 Handler，零侵入。

---

## 4. 工厂方法模式 (Factory Method)

**使用位置**: `RpcMessage.buildRequest()` / `RpcMessage.buildResponse()`

**解决的问题**: `RpcMessage` 的构建涉及协议头填充（Magic、Version、MessageType 等）。直接暴露构造细节会导致调用方代码冗长且易出错。

**实现方式**: 静态工厂方法封装完整的构建逻辑：

```java
RpcMessage msg = RpcMessage.buildRequest(request);   // 自动填充协议头
RpcMessage resp = RpcMessage.buildResponse(response); // 自动映射 status
```

**为何适合**: 协议消息的构建规则是固定的，工厂方法集中管理这些规则，保证一致性。

---

## 5. 观察者模式 / 异步回调 (Observer / Callback)

**使用位置**: `PendingRequests` + `CompletableFuture` + `ChannelFutureListener`

**解决的问题**: Netty 底层是异步的，但 RPC 调用方期望同步获得结果。需要一个中间机制将异步 I/O 事件与阻塞等待的业务线程关联。

**实现方式**:

```
发送时: PendingRequests.put(requestId) → 返回 CompletableFuture
           ↓
接收时: RpcClientHandler.channelRead0() → PendingRequests.complete(requestId, response)
           ↓
调用方: future.get(timeout) ← 被唤醒并获得结果
```

**为何适合**: `CompletableFuture` 是 JDK 原生的异步编排工具，天然支持超时、异常传播、链式组合。

---

## 6. 模板方法 / 依赖注入 (Template + DI)

**使用位置**: `Serializer` 接口注入到 `RpcEncoder` / `RpcDecoder`

**解决的问题**: 编解码器的帧处理逻辑是固定的（读写头部、读写 Body），但 Body 的序列化/反序列化算法可变。

**实现方式**: Encoder/Decoder 的 `encode()`/`decode()` 方法是模板，其中调用 `serializer.serialize()`/`deserialize()` 是可变步骤，通过构造函数注入具体实现。

```java
public class RpcEncoder extends MessageToByteEncoder<RpcMessage> {
    private final Serializer serializer;  // 注入的策略
    // encode() 中固定的帧头写入 + 可变的 body 序列化
}
```

**为何适合**: 帧协议格式稳定，但序列化策略可能演进。分离使得两者独立变化。
