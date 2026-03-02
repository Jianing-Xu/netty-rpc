# Design Patterns Document

[中文文档](../zh/design-patterns_zh.md)

## 1. Proxy Pattern

**Location**: `RpcClientProxy`

**Problem Solved**: Allows business code to invoke remote methods via interfaces as transparently as invoking local methods, shielding underlying details such as serialization, network transmission, and service discovery.

**Implementation**: JDK's `java.lang.reflect.Proxy` generates proxy instances for any interface at runtime. The `InvocationHandler` intercepts each method call, wraps it into an `RpcRequest`, and sends it via `NettyClient`.

```java
// From the perspective of business code — completely unaware of underlying RPC mechanics
HelloService service = proxy.create(HelloService.class);
String result = service.sayHello("World");  // Triggers actual network request
```

**Why it fits**: The core value proposition of an RPC framework is "localizing remote calls." Dynamic proxies are the most natural pattern to achieve this goal.

---

## 2. Strategy Pattern

**Location**: The `LoadBalancer` interface and its implementations (`RandomLoadBalancer`, `RoundRobinLoadBalancer`, `ConsistentHashLoadBalancer`).

**Problem Solved**: The algorithm for selecting a target node from multiple available service providers might change based on business scenarios (e.g., weighted, consistent hashing).

**Implementation**: Defining the `LoadBalancer` interface. Different routing algorithms serve as independent implementation classes and are replaced via constructor injection.

```java
// Choosing a strategy during construction
RpcBootstrap bootstrap = RpcBootstrap.builder()
        .loadBalancer(new ConsistentHashLoadBalancer())
        // Switching strategies requires no changes to other code
        .build();
```

**Why it fits**: It adheres to the Open/Closed Principle. Adding a new strategy simply requires implementing the interface. Load balancing is a classic "family of algorithms" scenario.

---

## 3. Chain of Responsibility Pattern

**Location**: The Handler chain within Netty's `ChannelPipeline`.

**Problem Solved**: Decoupling the stages of network data processing (receiving, decoding, business execution, encoding, sending) into independent, sequential steps.

**Implementation**: Netty's Pipeline is natively a Chain of Responsibility:

```text
Inbound:  ByteBuf → RpcDecoder → RpcServerHandler
Outbound: RpcMessage → RpcEncoder → ByteBuf
```

Each handler purely focuses on its own responsibility (decode / logic / encode), passing data sequentially down the chain.

**Why it fits**: Processing network protocols is inherently multi-staged. The Pipeline pattern enables adding new processing steps (e.g., compression, authentication) merely by inserting a Handler without intruding on existing code.

---

## 4. Factory Method Pattern

**Location**: `RpcMessage.buildRequest()` / `RpcMessage.buildResponse()`

**Problem Solved**: Constructing an `RpcMessage` requires populating protocol headers (Magic, Version, MessageType, etc.). Exposing construction details directly leads to verbose and error-prone caller code.

**Implementation**: Static factory methods encapsulate the complete construction logic:

```java
RpcMessage msg = RpcMessage.buildRequest(request);   // Automatically populates header
RpcMessage resp = RpcMessage.buildResponse(response); // Automatically maps status
```

**Why it fits**: The construction rules for protocol messages are rigid. Factory methods centrally manage these rules to guarantee consistency.

---

## 5. Observer Pattern / Asynchronous Callbacks

**Location**: `PendingRequests` + `CompletableFuture` + `ChannelFutureListener`

**Problem Solved**: Netty's underlying operations are asynchronous, but RPC callers expect to synchronously obtain results. An intermediary mechanism is required to associate asynchronous I/O events with blocking business threads.

**Implementation**:

```text
Sending: PendingRequests.put(requestId) → Returns CompletableFuture
           ↓
Receiving: RpcClientHandler.channelRead0() → PendingRequests.complete(requestId, response)
           ↓
Caller: future.get(timeout) ← Thread awakened and obtains result
```

**Why it fits**: `CompletableFuture` is Java's native asynchronous orchestration tool, inherently supporting timeouts, exception propagation, and chain composition.

---

## 6. Template Method / Dependency Injection

**Location**: `Serializer` interface mapped into `RpcEncoder` / `RpcDecoder`

**Problem Solved**: The frame processing logic for codecs is rigid (writing/reading headers, writing/reading the body), but the algorithm used to serialize/deserialize the body is interchangeable.

**Implementation**: The `encode()`/`decode()` procedures behave as skeleton templates. Within them, executing `serializer.serialize()` is a variable step, delegated to a concrete implementation injected via constructors.

```java
public class RpcEncoder extends MessageToByteEncoder<RpcMessage> {
    private final Serializer serializer;  // Injected strategy
    // In encode(): fixed header writes + variable body serialization via `serializer`
}
```

**Why it fits**: Frame protocol structures are stable, but serialization strategies might evolve. Decoupling them allows independent advancement.
