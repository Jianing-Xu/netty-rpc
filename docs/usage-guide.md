# 使用指南

## 1. 快速开始

### 1.1 前置条件

- Java 17+
- Maven 3.8+
- ZooKeeper 运行中（本地 Docker：`docker start dev-zookeeper`，默认端口 2181）

### 1.2 构建项目

```bash
cd netty-rpc
mvn clean install -DskipTests
```

---

## 2. 定义服务接口

服务接口必须放在 Provider 和 Consumer 均可访问的公共路径中。接口须实现 `Serializable`（因参数/返回值需通过网络传输）。

```java
package com.xujn.nettyrpc.example.api;

public interface HelloService {
    String sayHello(String name);
    int add(int a, int b);
}
```

---

## 3. 实现服务（Provider 端）

```java
package com.xujn.nettyrpc.example.provider;

import com.xujn.nettyrpc.example.api.HelloService;

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

## 4. 启动服务端

```java
package com.xujn.nettyrpc.example.provider;

import com.xujn.nettyrpc.application.server.RpcServer;
import com.xujn.nettyrpc.example.api.HelloService;
import com.xujn.nettyrpc.infrastructure.registry.ZkServiceRegistry;
import com.xujn.nettyrpc.infrastructure.serialization.JdkSerializer;

public class ServerApp {
    public static void main(String[] args) throws Exception {
        // 1. 初始化组件
        var serializer = new JdkSerializer();
        var registry = new ZkServiceRegistry("127.0.0.1:2181");

        // 2. 创建 RPC 服务器
        var server = new RpcServer("127.0.0.1", 8080, registry, serializer);

        // 3. 注册服务
        server.addService(HelloService.class.getName(), new HelloServiceImpl());

        // 4. 启动
        server.start();
        System.out.println("Server started on 127.0.0.1:8080");

        // JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
    }
}
```

---

## 5. 启动客户端

```java
package com.xujn.nettyrpc.example.consumer;

import com.xujn.nettyrpc.application.client.NettyClient;
import com.xujn.nettyrpc.application.client.RpcClientProxy;
import com.xujn.nettyrpc.example.api.HelloService;
import com.xujn.nettyrpc.infrastructure.loadbalance.RoundRobinLoadBalancer;
import com.xujn.nettyrpc.infrastructure.registry.ZkServiceDiscovery;
import com.xujn.nettyrpc.infrastructure.serialization.JdkSerializer;

public class ClientApp {
    public static void main(String[] args) {
        // 1. 初始化组件
        var serializer = new JdkSerializer();
        var discovery = new ZkServiceDiscovery("127.0.0.1:2181");
        var loadBalancer = new RoundRobinLoadBalancer();
        var nettyClient = new NettyClient(serializer);

        // 2. 创建代理工厂（超时 5 秒）
        var proxy = new RpcClientProxy(discovery, loadBalancer, nettyClient, 5000);

        // 3. 生成代理对象
        HelloService helloService = proxy.create(HelloService.class);

        // 4. 像本地方法一样调用
        String result = helloService.sayHello("Netty RPC");
        System.out.println(result);  // 输出: Hello, Netty RPC!

        int sum = helloService.add(10, 20);
        System.out.println("10 + 20 = " + sum);  // 输出: 10 + 20 = 30

        // 5. 关闭
        nettyClient.shutdown();
    }
}
```

---

## 6. 多 Provider 部署

同一服务可部署多个实例，客户端自动通过负载均衡选择：

```java
// Provider A (端口 8080)
var serverA = new RpcServer("127.0.0.1", 8080, registry, serializer);
serverA.addService(HelloService.class.getName(), new HelloServiceImpl());
serverA.start();

// Provider B (端口 8081)
var serverB = new RpcServer("127.0.0.1", 8081, registry, serializer);
serverB.addService(HelloService.class.getName(), new HelloServiceImpl());
serverB.start();

// Client 端无需修改，discovery 自动获取两个地址，loadBalancer 自动轮询
```

---

## 7. 切换负载均衡策略

```java
// 随机策略
var proxy = new RpcClientProxy(discovery, new RandomLoadBalancer(), client, 5000);

// 轮询策略
var proxy = new RpcClientProxy(discovery, new RoundRobinLoadBalancer(), client, 5000);
```

---

## 8. 异常处理

```java
try {
    String result = helloService.sayHello("test");
} catch (RpcException e) {
    // 服务端业务异常 / 服务不存在 / 网络异常
    System.err.println("RPC call failed: " + e.getMessage());
} catch (Exception e) {
    // TimeoutException → 超时
    // 其他未预期异常
    System.err.println("Unexpected error: " + e.getMessage());
}
```

---

## 9. 配置参数

| 参数 | 位置 | 默认值 | 说明 |
|------|------|--------|------|
| `timeoutMs` | `RpcClientProxy` 构造 | 5000 | RPC 调用超时（毫秒） |
| `CONNECT_TIMEOUT_MILLIS` | `NettyClient` | 5000 | TCP 连接超时 |
| `sessionTimeoutMs` | `ZkServiceRegistry` | 5000 | ZK 会话超时 |
| `connectionTimeoutMs` | `ZkServiceRegistry` | 3000 | ZK 连接超时 |
| BossGroup 线程数 | `RpcServer` | 1 | Netty Accept 线程 |
| WorkerGroup 线程数 | `RpcServer` | CPU*2 | Netty I/O 线程 |
| 业务线程池大小 | `RpcServerHandler` | CPU*2 | 反射调用隔离线程 |
