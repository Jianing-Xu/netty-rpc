# Development Guide

[中文文档](../zh/development-guide_zh.md)

## 1. Environment Requirements

| Dependency | Required Version |
|------------|------------------|
| JDK | 17+ |
| Maven | 3.8+ |
| ZooKeeper | 3.7+ (Dev/Prod environment) |

> Unit tests use Curator's `TestingServer` (embedded ZK), so no external ZK instance is required to run tests.

---

## 2. Project Structure

```text
netty-rpc/
├── pom.xml                              # Parent POM (Dependency management + JaCoCo)
│
├── netty-rpc-domain/                    # Domain Layer (No external dependencies)
│   └── src/main/java/com/xujn/nettyrpc/domain/
│       ├── model/                       # Requests, Responses, Protocols, Contexts
│       ├── protocol/                    # Serializer (I)
│       ├── registry/                    # Registry/Discovery (I)
│       ├── loadbalance/                 # LoadBalancer (I)
│       ├── annotation/                  # @RpcService, @RpcReference
│       └── exception/                   # Exceptions
│
├── netty-rpc-infrastructure/            # Infrastructure Layer (Depends on Domain + Netty + ZK)
│   └── src/main/java/com/xujn/nettyrpc/infrastructure/
│       ├── serialization/               # JdkSerializer, ProtobufSerializer
│       ├── codec/                       # RpcEncoder, RpcDecoder
│       ├── registry/                    # ZK implementations
│       └── loadbalance/                 # Random, RoundRobin, ConsistentHash
│
├── netty-rpc-application/               # Application Layer (Depends on Domain + Infra)
│   └── src/main/java/com/xujn/nettyrpc/application/
│       ├── client/                      # Proxies, Request Management
│       ├── server/                      # Server binding, Request routing
│       └── bootstrap/                   # RpcBootstrap annotations engine
│
├── netty-rpc-examples/                  # Example Layer
│   └── src/main/java/com/xujn/nettyrpc/examples/
│       ├── api/                         # Shared Interface
│       ├── provider/                    # Annotated Provider
│       └── consumer/                    # Annotated Consumer
│
└── docs/                                # Documentation
```

---

## 3. Build & Test Commands

### 3.1 Full Build

```bash
mvn clean install
```

### 3.2 Running Tests

```bash
# Run all tests
mvn clean test

# specifically targeting a module
mvn clean test -pl netty-rpc-domain
mvn clean test -pl netty-rpc-infrastructure -am
mvn clean test -pl netty-rpc-application -am
```

### 3.3 Coverage Reports

```bash
mvn clean test jacoco:report
# Path: {module}/target/site/jacoco/index.html
```

JaCoCo defines a `check` execution. If line coverage in any module **drops below 80%**, the build will automatically fail.

### 3.4 Skip Coverage Check (Temporary)

```bash
mvn clean test -Djacoco.skip=true
```

---

## 4. TDD Workflow

This project adheres to the Red-Green-Refactor loop:

```text
1. Red      → Write a failing test verifying expected behavior
2. Green    → Write the simplest code to pass the test
3. Refactor → Refactor code while retaining a Green state for all tests
```

### Testing Strategy

| Layer | Method | Example |
|-------|--------|---------|
| Domain | Pure Unit Tests | `RpcRequestTest` (verifying constructors/equality) |
| Infrastructure | Component Tests | `RpcCodecTest` (using `EmbeddedChannel`) |
| Infrastructure | Integration Tests | `ZkRegistryTest` (using Curator `TestingServer`) |
| Application | Unit Tests | `RpcServerHandlerTest` (verifying reflections) |
| Application | End-to-End Tests | `IntegrationTest` (firing full Client → Protocol → Node → Client loops) |

---

## 5. Extension Guidelines

### 5.1 Adding a New Serializer

1. Implement `Serializer` in `netty-rpc-infrastructure`:

```java
package com.xujn.nettyrpc.infrastructure.serialization;

public class GsonSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj) { /* Gson logic */ }
    @Override
    public Object deserialize(byte[] data) { /* Gson logic */ }
}
```

2. Register a new ID byte in `SerializationType` enum.
3. Inject the new Serializer instance when constructing `RpcBootstrap` / `RpcServer` / `NettyClient`.

### 5.2 Adding a New Load Balancer

1. Implement `LoadBalancer`:

```java
public class WeightedRandomLoadBalancer implements LoadBalancer {
    @Override
    public String select(List<String> serviceAddresses) { /* Weight logic */ }
}
```

2. Configure `RpcBootstrap` or `RpcClientProxy` with the new mechanism.

### 5.3 Publishing RPC Services

Implement your interface, inject it via `@RpcService`, and scan via `RpcBootstrap` (Refer to the Usage Guide). The framework code remains untouched.

---

## 6. Design Trade-offs & Boundaries

### 6.1 Current Implementations

| Decision | Approach | Rationale |
|----------|----------|-----------|
| Serialization | Protostuff (Default) | High performance, tiny payload, works without schema creation. Retained JDK as backup. |
| Conn. Mng | Channel Mapping | Comprehensive connection-pooling was overkill. `Map<address, Channel>` suffices. |
| Discovery | Real-time ZK Hits | Scaled networks require caching; isolated `ServiceDiscovery` allows later addition without impact. |
| Bootstrapping| `@RpcService` Scans | `RpcBootstrap` brings Spring-like declarative UX into a pure Java app. |

### 6.2 Future Enhancements

| Enhancement | Description |
|-------------|-------------|
| Heartbeats | `IdleStateHandler` implementation to ping idle TCP connections natively. |
| Connection Pools | Incorporate native Netty `FixedChannelPool`. |
| Local Routing Caches | Attach `CuratorCache` listeners storing updated IPs locally to reduce ZK pressure. |
| Auto-Retries | Configurable retry mechanics inside `RpcClientProxy` interceptors. |
| SPI Extensibility | Use Java `ServiceLoader` to seamlessly detect 3rd-party Balancer/Serializer JARs dynamically. |
| Async Return Values | Provide native integrations mapping to User `CompletableFuture<T>` responses. |
