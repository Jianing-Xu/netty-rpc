# Architecture & Design Document

[中文文档](../zh/architecture_zh.md)

## 1. Project Overview

`netty-rpc` is an enterprise-grade RPC framework built on Java 17, Netty 4.1.x, and ZooKeeper. The project adopts a **Microkernel Architecture (Core Engine + SPI Plugins)** and uses **TDD (Test-Driven Development)**, covering core RPC components: communication protocols, codecs, service registry/discovery, proxied invocation, load balancing, and fault tolerance via timeouts.

---

## 2. Technology Stack Selection

| Tech Stack | Rationale |
|-----------|-----------|
| **Java 17** | LTS release featuring better GC performance, sealed classes, and pattern matching. |
| **Netty 4.1.x** | High-performance async event-driven framework providing robust ByteBuf management, ChannelPipeline, and Boss/Worker reactor models. |
| **Apache Curator 5.5.0** | Industrial-grade Zookeeper client encapsulating retry policies, session management, and watchers. |
| **Protostuff** | High-performance, schema-less Protobuf serialization strategy by default. |
| **JUnit 5 + Mockito** | Modern testing tools supporting parameterized tests and mock injections. |
| **JaCoCo 0.8.11** | Maven-native coverage tool used via the `check` goal to enforce required coverage thresholds. |

**Technologies explicitly avoided**: Spring/Spring Boot, TLS security, Streaming RPC, Multi-language SDKs.

---

## 3. Architecture Design

### 3.1 Microkernel Architecture (Core + SPI)

```text
┌─────────────────────────────────────────────────────────────────┐
│                    netty-rpc-core (Core Engine)                 │
│  Responsibilities: Bootstrapping, proxies, transports, futures  │
│                                                                 │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐          │
│  │RpcBootstrap │ │ NettyClient  │ │  RpcServer        │         │
│  │(Annotations)│ │ (Connection) │ │  (Startup)        │         │
│  └──────┬──────┘ └──────┬───────┘ └───────┬──────────┘          │
│         │               │                 │                     │
│  ┌──────┴──────┐ ┌──────┴───────┐ ┌───────┴──────────┐         │
│  │PendingReqs  │ │RpcClientProxy│ │RpcServerHandler   │        │
│  │ (Futures)   │ │ (Invocation) │ │ (Reflection + TP) │        │
│  └─────────────┘ └──────────────┘ └──────────────────┘          │
├─────────────────────────────────────────────────────────────────┤
│                   netty-rpc-api (SPI Interfaces)                │
│  Responsibilities: Pure technology contracts decoupling domains │
│                                                                 │
│  ┌─────────────┐ ┌────────────────┐ ┌──────────────────┐      │
│  │ Serializer   │ │ LoadBalancer   │ │ServiceRegistry    │      │
│  │             │ │                │ │ServiceDiscovery   │      │
│  └─────────────┘ └────────────────┘ └──────────────────┘      │
├─────────────────────────────────────────────────────────────────┤
│                   Plugin Implementations                        │
│  Responsibilities: Pluggable technical implementations          │
│                                                                 │
│  [netty-rpc-registry-zk]     -> Curator + ZooKeeper bindings    │
│  [netty-rpc-core built-ins]  -> Netty Codecs, Protostuff logic  │
├─────────────────────────────────────────────────────────────────┤
│                      netty-rpc-common                           │
│  Responsibilities: Base models, exceptions, shareable network   │
│                    constants.                                   │
│                                                                 │
│  Models:  RpcRequest, RpcResponse, RpcMessage, Header          │
│  Enums:   MessageType, SerializationType, ProtocolConstants     │
│  Annots:  @RpcService, @RpcReference                            │
│  Except:  RpcException                                          │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Client Architecture

```text
Business Logic ──▶ RpcClientProxy (JDK Proxy)
                  │
                  ├─ 1. Build RpcRequest (requestId, class, method, args)
                  ├─ 2. ServiceDiscovery.discover() → Address List
                  ├─ 3. LoadBalancer.select() → Target host:port
                  ├─ 4. NettyClient.sendRequest() → CompletableFuture
                  │        │
                  │        ├─ PendingRequests.put(requestId)
                  │        ├─ RpcMessage.buildRequest() encapsulates frame
                  │        └─ Channel.writeAndFlush(msg) sends frame
                  │
                  └─ 5. future.get(timeout) synchronous blocking wait
```

### 3.3 Server Architecture

```text
Netty Bootstrap (Boss=1, Worker=CPU*2)
    │
    ├─ RpcDecoder     ← Decodes binary frames to RpcMessage
    ├─ RpcEncoder     ← Encodes RpcMessage to binary frames
    └─ RpcServerHandler
         │
         ├─ Extract RpcRequest from RpcMessage
         ├─ Submit to Business Thread Pool (Prevents NIO block)
         ├─ serviceMap.get(className) finds instance
         ├─ Method.invoke() reflection
         ├─ Build RpcResponse (success/error)
         └─ ctx.writeAndFlush(RpcMessage.buildResponse())
```

### 3.4 Registry Interaction Model

```text
ZooKeeper Tree:

/netty-rpc
  └── /services
        ├── /com.example.HelloService        ← PERSISTENT
        │     ├── /127.0.0.1:8080            ← EPHEMERAL
        │     └── /127.0.0.1:8081            ← EPHEMERAL
        └── /com.example.UserService         ← PERSISTENT
              └── /192.168.1.10:9090         ← EPHEMERAL
```

- **Registration**: Server starts and uses `ZkServiceRegistry` to create an EPHEMERAL sequential node under its service path.
- **Discovery**: Client uses `ZkServiceDiscovery` to `getChildren()` under the service path.
- **Auto-Removal**: Server crash or shutdown triggers a ZK Session expiration, which automatically deletes EPHEMERAL nodes.

---

## 4. Communication Protocol

### 4.1 Frame Structure

```text
+--------+--------+--------+--------+--------+--------+----------+---------+---------+
| Magic  |Version | MsgType| SerType| Status |      RequestId     | BodyLen |  Body   |
| 2 Byte | 1 Byte | 1 Byte | 1 Byte | 1 Byte |     8 Bytes       | 4 Bytes | N Bytes |
+--------+--------+--------+--------+--------+--------------------+---------+---------+
|<--------------------- Header (18 Bytes) --------------------------------->|< Body  >|
```

### 4.2 Field Definitions

| Field | Bytes | Type | Description |
|-------|-------|------|-------------|
| Magic | 2 | short | Magic number `0xCAFE`, used to filter invalid TCP connections. |
| Version | 1 | byte | Protocol version (`1`). |
| MsgType | 1 | byte | 0=Request, 1=Response, 2=Heartbeat. |
| SerType | 1 | byte | Serialization type (0=JDK, 1=Protobuf). |
| Status | 1 | byte | Response status (0=Success, 1=Exception). |
| RequestId | 8 | long | Unique ID linking asynchronous requests and responses. |
| BodyLen | 4 | int | Length of the serialized body array. |
| Body | N | bytes | Serialized `RpcRequest` or `RpcResponse`. |

### 4.3 TCP Packet Handling

`RpcDecoder` addresses TCP fragmentation via:

1. **Header validation**: Wait until at least 18 readable bytes are present.
2. **Mark/Reset**: Use `markReaderIndex()`. Read the header. If the body length isn't fully available, invoke `resetReaderIndex()` and exit.
3. **Magic Check**: If bounds exist but magic number != `0xCAFE`, close connection instantly.

---

## 5. Threading Model

### 5.1 Netty Groups

```text
Server Side:
  BossGroup (1 thread)      ← Accept events
  WorkerGroup (CPU*2)       ← Read/Write I/O

Client Side:
  EventLoopGroup (4 threads)← Shared among all Channel endpoints
```

### 5.2 Business Thread Isolation

`RpcServerHandler` dispatches `RpcRequest`s to an independent Java `ExecutorService`:

```java
bizThreadPool.submit(() -> {
    Object result = invokeService(request);   // Potential DB block
    ctx.writeAndFlush(responseMsg);
});
```

**Reasoning**: Reflection invocations might block. Processing them on Netty Worker threads would freeze all other concurrent channels sharing that thread.

### 5.3 Asynchronous Future Management

`PendingRequests` implements lock-free request mapping using `ConcurrentHashMap<Long, CompletableFuture<RpcResponse>>`:

```text
Send Phase → put(requestId, future)        [Netty Worker]
Recv Phase → remove(requestId) + complete  [Netty Worker]
Wait Phase → future.get(timeout)           [Business Caller Thread]
```

---

## 6. Fault Tolerance & Timeouts

| Scenario | Strategy |
|----------|----------|
| **Network Timeout** | `CompletableFuture.get(timeoutMs, MILLISECONDS)` throws `TimeoutException` |
| **Connection Refused** | `Bootstrap.connect()` honors `CONNECT_TIMEOUT_MILLIS=5000` |
| **Server Error** | Exceptions are embedded in `RpcResponse.error` and thrown locally by the client |
| **Service Not Found** | ZK raises exception directly |
| **Provider Death** | EPHEMERAL ZK node dies, and the client Discovery fetches a newly healthy list on the next call |
| **Channel Disconnect**| Sent via `closeFuture` eviction logic in caching components |

---

## 7. Load Balancing Strategies

| Policy | Class | Algorithm |
|--------|-------|-----------|
| Random | `RandomLoadBalancer` | `ThreadLocalRandom.nextInt(size)` |
| RoundRobin | `RoundRobinLoadBalancer` | Atomic increment & overflow-safe modulo (`& Integer.MAX_VALUE`). |
| ConsistentHash | `ConsistentHashLoadBalancer` | 160 virtual nodes distributed over a consistent hash ring using `FNV1a_32` hashing. |

All policies implement the `LoadBalancer` Domain interface and can be injected flexibly into `RpcBootstrap`.
