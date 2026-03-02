# netty-rpc

[English Document](README.md)

基于 Java 17、Netty 4.1.x 与 ZooKeeper 的轻量级高性能 RPC 框架。采用微内核架构（核心分离机制 + SPI 插件扩展），并以 TDD 驱动开发，全模块测试覆盖率 >80%。

## 架构

```text
┌────────────────────────────────────────────────────────────┐
│                  netty-rpc-core (核心执行引擎)               │
│  RpcBootstrap        PendingRequests       RpcClientProxy  │
│  RpcServerHandler    NettyClient / Server  Codec           │
├────────────────────────────────────────────────────────────┤
│                  netty-rpc-api (SPI 扩展规范)               │
│  Serializer      LoadBalancer    Registry      Discovery   │
├────────────────────────────────────────────────────────────┤
│                  插件实现 (Plugin Implementations)          │
│  [netty-rpc-registry-zk]   (ZooKeeper/Curator)             │
│  [Future: nacos-registry, kryo-serializer...]              │
├────────────────────────────────────────────────────────────┤
│                  netty-rpc-common                          │
│  RpcRequest/Response       @RpcService / @RpcReference     │
└────────────────────────────────────────────────────────────┘
```

## 技术栈

| 组件        | 技术               | 版本           |
|------------|-------------------|---------------|
| 网络通信    | Netty             | 4.1.108.Final |
| 注册中心    | ZooKeeper/Curator | 5.5.0         |
| 序列化      | Protostuff        | 1.8.0         |
| 构建工具    | Maven             | 3.x           |
| 测试框架    | JUnit 5 + Mockito | 5.10.2        |
| 覆盖率      | JaCoCo            | 0.8.11        |

## 自定义协议

18 字节固定头部 + 变长消息体：

```
| Magic(2) | Version(1) | MsgType(1) | SerType(1) | Status(1) | RequestId(8) | BodyLen(4) | Body(N) |
```

- **Magic**: `0xCAFE`，用于帧校验
- **MsgType**: 0=Request, 1=Response, 2=Heartbeat
- **Status**: 0=成功, 1=异常

## RPC 调用流程

1. 客户端通过 `RpcClientProxy` 生成 JDK 动态代理
2. 代理拦截方法调用，封装为 `RpcRequest`
3. `ServiceDiscovery` 从 ZooKeeper 获取可用地址
4. `LoadBalancer` 选择目标节点
5. `NettyClient` 通过自定义协议编码发送，`PendingRequests` 注册 `CompletableFuture`
6. 服务端 `RpcDecoder` 解码，`RpcServerHandler` 在业务线程池中反射调用
7. 响应经 `RpcEncoder` 编码返回客户端
8. 客户端 `RpcClientHandler` 根据 `requestId` 完成对应 Future

## 快速开始

### 前置条件

- Java 17+
- Maven 3.x
- ZooKeeper（可通过 Docker 启动: `docker start dev-zookeeper`）

### 构建 & 测试

```bash
mvn clean test
```

### 覆盖率报告

```bash
mvn clean test jacoco:report
# 报告位于 {module}/target/site/jacoco/index.html
```

## 模块说明

| 模块                        | 职责                                           |
|----------------------------|----------------------------------------------|
| `netty-rpc-common`         | 公共模型、自定义注解、全局异常定义等共享数据源        |
| `netty-rpc-api`            | 核心 SPI 扩展接口定义（解耦技术实现依赖）              |
| `netty-rpc-core`           | RPC 引擎基座（Netty 通信、动态代理、反射调用与组装）  |
| `netty-rpc-registry-zk`    | 基于 ZK Curator 的服务注册与发现插件实现             |
| `netty-rpc-examples`       | 演示案例                                      |

## 设计模式

- **动态代理模式**: `RpcClientProxy` — 透明化远程调用
- **策略模式**: `LoadBalancer` — 可插拔的负载均衡策略
- **责任链模式**: Netty `ChannelPipeline` — 编解码与业务处理链式解耦
- **工厂方法模式**: `RpcMessage.buildRequest/buildResponse` — 封装消息构造

## 测试覆盖率

| 模块            | 覆盖率 | 测试数 |
|----------------|-------|-------|
| Common         | 92%   | 21    |
| API            | 100%  | 0     |
| Core           | >85%  | 40    |
| Registry(ZK)   | >85%  | 2     |
| **总计**        | **>80%** | **63** |
