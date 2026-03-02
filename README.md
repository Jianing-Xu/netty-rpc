# netty-rpc

[中文文档](README_zh.md)

A lightweight, high-performance RPC framework based on Java 17, Netty 4.1.x, and ZooKeeper. Built with Domain-Driven Design (DDD) layering and Test-Driven Development (TDD) principles, boasting >80% test coverage across all modules.

## Architecture

```text
┌──────────────────────────────────────────────────────────────────────┐
│                     netty-rpc-application                           │
│  RpcBootstrap (@RpcService / @RpcReference)                         │
│  RpcClientProxy ←→ NettyClient ←→ PendingRequests                   │
│  RpcServer ←→ RpcServerHandler (Reflection + Business Thread Pool)  │
├──────────────────────────────────────────────────────────────────────┤
│                    netty-rpc-infrastructure                         │
│  RpcEncoder/Decoder   Jdk/ProtobufSerializer   ZkRegistry/Discovery │
│  Random/RoundRobin/ConsistentHash LoadBalancer                      │
├──────────────────────────────────────────────────────────────────────┤
│                       netty-rpc-domain                              │
│  RpcRequest/Response  RpcMessage  ProtocolConstants  MessageType     │
│  Serializer(I)  ServiceRegistry(I)  ServiceDiscovery(I)  LoadBalancer(I)│
└──────────────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Component         | Technology         | Version       |
|------------------|-------------------|---------------|
| Network Comm     | Netty             | 4.1.108.Final |
| Service Registry | ZooKeeper/Curator | 5.5.0         |
| Serialization    | Protostuff        | 1.8.0         |
| Build Tool       | Maven             | 3.x           |
| Testing          | JUnit 5 + Mockito | 5.10.2        |
| Coverage         | JaCoCo            | 0.8.11        |

## Custom Protocol

18-byte fixed header + variable length body:

```text
| Magic(2) | Version(1) | MsgType(1) | SerType(1) | Status(1) | RequestId(8) | BodyLen(4) | Body(N) |
```

- **Magic**: `0xCAFE`, for filtering invalid frames.
- **MsgType**: 0=Request, 1=Response, 2=Heartbeat
- **Status**: 0=Success, 1=Exception

## RPC Invocation Flow

1. Client generates a JDK dynamic proxy via `RpcClientProxy`.
2. The proxy intercepts the method call and encapsulates it into an `RpcRequest`.
3. `ServiceDiscovery` retrieves available addresses from ZooKeeper.
4. `LoadBalancer` selects the target host:port.
5. `NettyClient` encodes and sends via custom protocol; `PendingRequests` registers a `CompletableFuture`.
6. Server decodes the message, and `RpcServerHandler` invokes the method via reflection using isolated business threads.
7. Response is encoded by `RpcEncoder` and returned to the client.
8. Client `RpcClientHandler` completes the corresponding Future based on the `requestId`.

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.x
- ZooKeeper (Docker: `docker start dev-zookeeper` on port 2181)

### Build & Test

```bash
mvn clean test
```

### Coverage Report

```bash
mvn clean test jacoco:report
# Reports generated at {module}/target/site/jacoco/index.html
```

## Modules

| Module                      | Responsibility |
|-----------------------------|----------------|
| `netty-rpc-domain`         | Core models, interfaces, exception definitions (No external dependencies) |
| `netty-rpc-infrastructure` | Netty codecs, ZK registry, serialization, load balancing implementations |
| `netty-rpc-application`    | Bootstrap, client proxy, server coordination, Future management |
| `netty-rpc-examples`       | Demo examples using `@RpcService` and `@RpcReference` |

## Design Patterns

- **Dynamic Proxy**: `RpcClientProxy` — transparent remote invocation.
- **Strategy**: `LoadBalancer` — pluggable routing algorithms.
- **Chain of Responsibility**: Netty `ChannelPipeline` — decoupling coding and business logic.
- **Factory Method**: `RpcMessage.buildRequest/buildResponse` — consistent message construction.

## Test Coverage

| Module         | Coverage | Tests |
|---------------|---------|-------|
| Domain        | 92%     | 21    |
| Infrastructure| >85%    | 48    |
| Application   | >85%    | 22    |
| **Total**     | **>80%**| **91**|
