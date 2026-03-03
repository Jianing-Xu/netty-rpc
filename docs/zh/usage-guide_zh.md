# 使用指南

[English Document](../en/usage-guide.md)

## 1. 快速开始

### 1.1 前置条件

- Java 17+
- Maven 3.8+
- ZooKeeper 运行中（本地开发可使用 Docker：`docker run -d --name zookeeper -p 2181:2181 zookeeper:3.7`）

### 1.2 构建项目

```bash
cd netty-rpc
mvn clean install -DskipTests
```

---

## 2. 定义公共服务接口

服务接口必须放在 Provider 和 Consumer 均可访问的公共模块中（如 `netty-rpc-example-api`）。接口对象必须实现 `Serializable`，或者使用 Protobuf 友好的 POJO。

```java
package com.xujn.nettyrpc.example.api;

import java.util.concurrent.CompletableFuture;

public interface HelloService {
    // 1. 同步阻塞调用接口
    String sayHello(String name);
    // 2. 异步非阻塞调用接口
    CompletableFuture<String> sayHelloAsync(String name);
}
```

---

## 3. 标准 Java 程序接入（无 Spring 依赖）

Netty-RPC 框架的核心不依赖 Spring，您可以完全在 Main 方法或原生 Java 程序中使用它。

### 3.1 启动服务端 (Provider)

可使用 `@RpcService` 注解标记服务实现类：

```java
import com.xujn.nettyrpc.common.annotation.RpcService;
import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.registry.zk.ZkServiceRegistry;

@RpcService(value = HelloService.class, limit = 1000) // 开启单机 QPS 限流为 1000
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

        // 自动扫描所在包下的 @RpcService 类并完成注册装配
        bootstrap.scanServices("com.xujn.nettyrpc.example.provider");
        bootstrap.startServer();

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::shutdown));
    }
}
```

> **注**：也可不使用扫包，手动通过 `bootstrap.addService(HelloService.class, new HelloServiceImpl(), 1000)` 直接注册。

### 3.2 启动客户端 (Consumer)

使用 `@RpcReference` 注解标识需注入的字段，并通过 `RpcBootstrap` 自动拉起代理：

```java
import com.xujn.nettyrpc.common.annotation.RpcReference;
import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.registry.zk.ZkServiceDiscovery;

public class ClientApp {

    @RpcReference(timeout = 3000, retries = 3) // 配置超时时间与失败重试次数
    private HelloService helloService;
    
    public void start() throws Exception {
        // 1. 发起同步调用
        System.out.println(helloService.sayHello("Netty RPC"));  

        // 2. 发起异步响应式调用
        helloService.sayHelloAsync("Netty Async").thenAccept(res -> {
            System.out.println("收到异步回调结果: " + res);
        });
    }

    public static void main(String[] args) throws Exception {
        RpcBootstrap bootstrap = RpcBootstrap.builder()
                .discovery(new ZkServiceDiscovery("127.0.0.1:2181"))
                .build();

        ClientApp app = new ClientApp();
        bootstrap.injectReferences(app); // 完成客户端代理实例的依赖注入

        try {
            app.start();
            Thread.sleep(1000); // 留点时间给异步回调
        } finally {
            bootstrap.shutdown();
        }
    }
}
```

---

## 4. Spring Boot 项目接入 (推荐)

框架内建了原生的 Spring Boot Starter，能够以最优雅的方式融入现有的微服务体系。

### 4.1 引入 Starter 依赖

```xml
<dependency>
    <groupId>com.xujn</groupId>
    <artifactId>netty-rpc-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 4.2 `application.yml` 全局配置

```yaml
netty-rpc:
  server:
    host: 127.0.0.1       # 本机暴露 IP 
    port: 8080            # 监听端口
  registry:
    address: 127.0.0.1:2181 # 服务端连接发现中心的地址
  client:
    discovery-address: 127.0.0.1:2181 # 客户端发现中心的地址
    timeout-ms: 5000      # 全局默认超时时间
    load-balancer: RoundRobin         # 负载均衡策略
```

### 4.3 开启 RPC 支持能力

在 Spring Boot 启动类上按需打上激活注解：
- 作为服务端（提供者）：加上 `@EnableRpcServer`
- 作为客户端（消费者）：加上 `@EnableRpcClient`

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

### 4.4 服务端开发 (Provider)
此时 `@RpcService` 将直接充当 Spring 的 `@Component` 注册容器：
```java
import com.xujn.nettyrpc.common.annotation.RpcService;

// 提供高并发原生的令牌桶限流 (1000/s) 支持
@RpcService(value = HelloService.class, limit = 1000)
public class SpringHelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) { return "Spring: " + name; }
}
```

### 4.5 客户端开发 (Consumer)
在 Controller 或任何由 Spring 管理的 Bean 中直接注入调用即可：
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

## 5. 高级特性用法

### 5.1 多实例 Provider 部署 (负载均衡)
同一服务可在多个不同端口/服务器部署。Consumer 会自动发现他们：
```java
// Client 端代码无需修改，只需替换 yml 或者 Bootstrap 里的属性
// 支持: RoundRobin (轮询默认)、Random (随机)、ConsistentHash (一致性哈希)
netty-rpc:
  client:
    load-balancer: ConsistentHash
```

### 5.2 熔断与异常处理
网络抖动或服务端过载被限流：
```java
try {
    String res = helloService.sayHello("Traffic Peak");
} catch (RpcException e) {
    // 处理如 "Server is busy. Rate limit exceeded..." 安全返回降级值
}
```
