# Usage Guide

[中文文档](../zh/usage-guide_zh.md)

## 1. Quick Start

### 1.1 Prerequisites

- Java 17+
- Maven 3.8+
- ZooKeeper running (Local dev via Docker: `docker run -d --name zookeeper -p 2181:2181 zookeeper:3.7`)

### 1.2 Build the Framework

```bash
cd netty-rpc
mvn clean install -DskipTests
```

---

## 2. Define Public Service Interface

Service API interfaces must reside within a common module accessible to both Consumer and Provider applications. The return object and arguments must implement `Serializable`, or be Protobuf-friendly POJOs.

```java
package com.xujn.nettyrpc.example.api;

import java.util.concurrent.CompletableFuture;

public interface HelloService {
    // 1. Synchronous blocking interface
    String sayHello(String name);
    // 2. Asynchronous non-blocking interface
    CompletableFuture<String> sayHelloAsync(String name);
}
```

---

## 3. Standard Java Program Integration (No Spring Dependency)

Netty-RPC framework core is independent of Spring; it can function robustly standalone in pure Java programs or Main methods.

### 3.1 Bootstrap the Server (Provider)

Use the `@RpcService` annotation to demarcate your implementation class:

```java
import com.xujn.nettyrpc.common.annotation.RpcService;
import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.registry.zk.ZkServiceRegistry;

@RpcService(value = HelloService.class, limit = 1000) // Enables TokenBucket rate limit of 1000 QPS
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) { return "Hello, " + name; }
    
    @Override
    public CompletableFuture<String> sayHelloAsync(String name) {
        return CompletableFuture.completedFuture("Hello Async, " + name); 
    }
}

public class ServerApp {
    public static void main(String[] args) throws Exception {
        RpcBootstrap bootstrap = RpcBootstrap.builder()
                .registry(new ZkServiceRegistry("127.0.0.1:2181"))
                .host("127.0.0.1")
                .port(8080)
                .build();

        // Performs dynamic classpath scanning for @RpcService annotated components 
        bootstrap.scanServices("com.xujn.nettyrpc.example.provider");
        bootstrap.startServer();

        // Register Graceful Shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::shutdown));
    }
}
```

### 3.2 Bootstrap the Client (Consumer)

Mark required service instances with the `@RpcReference` annotation and kick-start dependencies parsing using `RpcBootstrap`:

```java
import com.xujn.nettyrpc.common.annotation.RpcReference;
import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.registry.zk.ZkServiceDiscovery;

public class ClientApp {

    @RpcReference(timeout = 3000, retries = 3) // Set fallback timeouts & retry fault-tolerance 
    private HelloService helloService;
    
    public void start() throws Exception {
        // 1. Synchronous invocation
        System.out.println(helloService.sayHello("Netty RPC"));  

        // 2. Pure Async Reactive Invocation
        helloService.sayHelloAsync("Netty Async").thenAccept(res -> {
            System.out.println("Received async callback resolving: " + res);
        });
    }

    public static void main(String[] args) throws Exception {
        RpcBootstrap bootstrap = RpcBootstrap.builder()
                .discovery(new ZkServiceDiscovery("127.0.0.1:2181"))
                .build();

        ClientApp app = new ClientApp();
        bootstrap.injectReferences(app); // Automates the proxy injection

        try {
            app.start();
            Thread.sleep(1000); // Leave a time slice for async threads to complete
        } finally {
            bootstrap.shutdown();
        }
    }
}
```

---

## 4. Spring Boot Program Integration (Recommended)

The framework is packaged with a native Spring Boot Starter making enterprise integrations exceptionally elegant in your existing micro-services environment.

### 4.1 Incorporate Starter Dependency

```xml
<dependency>
    <groupId>com.xujn</groupId>
    <artifactId>netty-rpc-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 4.2 `application.yml` Global Configuration

```yaml
netty-rpc:
  server:
    host: 127.0.0.1       # Self-exposing Provider IP 
    port: 8080            # Listening Port
  registry:
    address: 127.0.0.1:2181 # Server registration center address
  client:
    discovery-address: 127.0.0.1:2181 # Client discovery center address
    timeout-ms: 5000      # Global fallback timeout 
    load-balancer: RoundRobin         # Distributed LoadBalancing Strategy
```

### 4.3 Empower RPC Capabilities

Place enablement modifiers seamlessly in your main `@SpringBootApplication` context:
- For Provider Context: append `@EnableRpcServer`
- For Consumer Context: append `@EnableRpcClient`

```java
@SpringBootApplication
@EnableRpcServer
@EnableRpcClient
public class SpringBootRpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringBootRpcApplication.class, args);
    }
}
```

### 4.4 Exposing Backend Service (Provider)
Your `@RpcService` now dual-acts natively as a Spring-managed `@Component`:
```java
import com.xujn.nettyrpc.common.annotation.RpcService;

// Integrates high-concurrency TokenBucket threshold limits
@RpcService(value = HelloService.class, limit = 1000)
public class SpringHelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) { return "Spring: " + name; }
}
```

### 4.5 Discovering Proxies via Spring (Consumer)
Consume via injecting fields across any bean elements such as Controllers or other nested Services:
```java
import com.xujn.nettyrpc.common.annotation.RpcReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    
    @RpcReference(retries = 3)
    private HelloService helloService;

    @GetMapping("/hello")
    public String hello(String name) {
        return helloService.sayHello(name);
    }
}
```

---

## 5. Advanced Feature Insights

### 5.1 Multi-Instance Load Balancing Deployment
Spawn identical services scaling over arbitrary topologies. The Consumer handles discoveries under-the-hood:
```yaml
# Inside Consumer application.yml 
# Available policies: RoundRobin (Default), Random, ConsistentHash
netty-rpc:
  client:
    load-balancer: ConsistentHash
```

### 5.2 Graceful Network Fault Handling
Trap framework generated `RpcException` upon networking jitters or server saturation limit denials:
```java
try {
    String res = helloService.sayHello("Traffic Flood");
} catch (RpcException e) {
    // Graceful fallback interception for: "Server is busy. Rate limit exceeded..." 
}
```
