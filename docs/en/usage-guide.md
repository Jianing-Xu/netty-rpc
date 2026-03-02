# Usage Guide

[中文文档](../zh/usage-guide_zh.md)

## 1. Quick Start

### 1.1 Prerequisites

- Java 17+
- Maven 3.8+
- ZooKeeper running (Local Docker: `docker start dev-zookeeper`, default port 2181)

### 1.2 Build the Framework

```bash
cd netty-rpc
mvn clean install -DskipTests
```

---

## 2. Define Service Interface

Service API interfaces must reside within a common module or path accessible to both the Consumer and the Provider applications.

```java
package com.xujn.nettyrpc.example.api;

public interface HelloService {
    String sayHello(String name);
    int add(int a, int b);
}
```

---

## 3. Implement the Service (Provider end)

Use the `@RpcService` annotation to mark your implementation class:

```java
package com.xujn.nettyrpc.example.provider;

import com.xujn.nettyrpc.example.api.HelloService;
import com.xujn.nettyrpc.common.annotation.RpcService;

@RpcService(HelloService.class)
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }

    @Override
    public int add(int a, int b) {
        return a + b;
    }
}
```

---

## 4. Bootstrapping the Server

Use `RpcBootstrap` to scan packages and automatically deploy your annotated services to Netty and ZooKeeper. (Default serialization: Protobuf).

```java
package com.xujn.nettyrpc.example.provider;

import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.registry.zk.ZkServiceRegistry;

public class ServerApp {
    public static void main(String[] args) throws Exception {
        // 1. Initialize Bootstrap
        RpcBootstrap bootstrap = RpcBootstrap.builder()
                .registry(new ZkServiceRegistry("127.0.0.1:2181"))
                .host("127.0.0.1")
                .port(8080)
                .build();

        // 2. Scan packages containing @RpcService
        bootstrap.scanServices("com.xujn.nettyrpc.example.provider");

        // 3. Start Server
        bootstrap.startServer();
        System.out.println("Server started on 127.0.0.1:8080");

        // 4. Register Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::shutdown));
    }
}
```

> **Note**: Alternative to scanning, manual registration works via `bootstrap.addService(HelloService.class, new HelloServiceImpl())`.

---

## 5. Bootstrapping the Client

Use the `@RpcReference` annotation to automatically inject proxy instances via `RpcBootstrap.injectReferences()`:

```java
package com.xujn.nettyrpc.example.consumer;

import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.example.api.HelloService;
import com.xujn.nettyrpc.common.annotation.RpcReference;
import com.xujn.nettyrpc.registry.zk.ZkServiceDiscovery;

public class ClientApp {

    // Default timeout: 5000ms. Overridden to 3000ms here.
    @RpcReference(timeout = 3000)
    private HelloService helloService;
    
    public void start() {
        // Use exactly like a local Java Method!
        String result = helloService.sayHello("Netty RPC");
        System.out.println(result);  // Output: Hello, Netty RPC!

        int sum = helloService.add(10, 20);
        System.out.println("10 + 20 = " + sum);  // Output: 10 + 20 = 30
    }

    public static void main(String[] args) {
        // 1. Initialize Bootstrap (Defaults to RoundRobin LB & Protobuf)
        RpcBootstrap bootstrap = RpcBootstrap.builder()
                .discovery(new ZkServiceDiscovery("127.0.0.1:2181"))
                .build();

        // 2. Process and inject @RpcReference fields
        ClientApp app = new ClientApp();
        bootstrap.injectReferences(app);

        try {
            app.start();
        } finally {
            // 3. Graceful Shutdown
            bootstrap.shutdown();
        }
    }
}
```

---

## 6. Multi-Provider Deployment

You can deploy multiple instances of the same service. Clients automatically balance the load:

```java
// Provider Instance A (Port 8080)
RpcBootstrap btA = RpcBootstrap.builder().host("127.0.0.1").port(8080).build();
// ... (start)

// Provider Instance B (Port 8081)
RpcBootstrap btB = RpcBootstrap.builder().host("127.0.0.1").port(8081).build();
// ... (start)

// Consumer Code remains 100% untouched. Discovery catches both, LB alternates between them.
```

---

## 7. Changing Load Balancing Strategies

The default is `RoundRobinLoadBalancer`. You can effortlessly switch policies—such as Consistent Hashing—during client Bootstrap building:

```java
import com.xujn.nettyrpc.core.loadbalance.ConsistentHashLoadBalancer;

RpcBootstrap bootstrap = RpcBootstrap.builder()
        .discovery(new ZkServiceDiscovery("127.0.0.1:2181"))
        .loadBalancer(new ConsistentHashLoadBalancer()) // Swap policy here!
        .build();
```

---

## 8. Exception Handling

```java
try {
    String result = helloService.sayHello("test");
} catch (RpcException e) {
    // Expected server logic exceptions / Target Offline / Protocol Decoding errors
    System.err.println("RPC call failed: " + e.getMessage());
} catch (Exception e) {
    // TimeoutException → No response returned in time
    // Undocumented network failures
    System.err.println("Unexpected error: " + e.getMessage());
}
```

---

## 9. Configuration Tuning

| Parameter | Location | Default | Description |
|-----------|----------|---------|-------------|
| `timeoutMs` | `@RpcReference` | 5000 | RPC invocation blocking timeout in ms |
| `CONNECT_TIMEOUT_MILLIS` | `NettyClient` | 5000 | Netty Bootstrap TCP timeout |
| `sessionTimeoutMs` | `ZkServiceRegistry` | 5000 | Curator Client ZK eviction threshold |
| BossGroup Thread Cnt | `RpcServer` | 1 | Reactor thread for TCP incoming accepts |
| WorkerGroup Thread Cnt| `RpcServer` | CPU*2 | Parallel I/O threads |
| Business Thread Pool | `RpcServerHandler` | CPU*2 | Reflected methods execution space |
