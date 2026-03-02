# 开发指南

[English Document](../en/development-guide.md)

## 1. 环境要求

| 依赖 | 版本要求 |
|------|---------|
| JDK | 17+ |
| Maven | 3.8+ |
| ZooKeeper | 3.7+（开发/生产环境） |

> 单元测试使用 Curator `TestingServer`（内嵌 ZK），无需外部 ZK 实例即可运行测试。

---

## 2. 项目结构

```
netty-rpc/
├── pom.xml                              # 父 POM (依赖管理 + JaCoCo)
│
├── netty-rpc-domain/                    # 领域层（无外部依赖）
│   └── src/main/java/com/xujn/nettyrpc/domain/
│       ├── model/                       # RpcRequest, RpcResponse, RpcMessage,
│       │                                # RpcProtocolHeader, ProtocolConstants,
│       │                                # MessageType, SerializationType
│       ├── protocol/                    # Serializer 接口
│       ├── registry/                    # ServiceRegistry, ServiceDiscovery 接口
│       ├── loadbalance/                 # LoadBalancer 接口
│       └── exception/                   # RpcException
│
├── netty-rpc-infrastructure/            # 基础设施层（依赖 Domain + Netty + Curator）
│   └── src/main/java/com/xujn/nettyrpc/infrastructure/
│       ├── serialization/               # JdkSerializer
│       ├── codec/                       # RpcEncoder, RpcDecoder
│       ├── registry/                    # ZkServiceRegistry, ZkServiceDiscovery
│       └── loadbalance/                 # RandomLoadBalancer, RoundRobinLoadBalancer
│
├── netty-rpc-application/               # 应用层（依赖 Domain + Infrastructure + Netty）
│   └── src/main/java/com/xujn/nettyrpc/application/
│       ├── client/                      # RpcClientProxy, NettyClient,
│       │                                # RpcClientHandler, PendingRequests
│       └── server/                      # RpcServer, RpcServerHandler
│
└── docs/                                # 项目文档
```

---

## 3. 构建与测试

### 3.1 全量构建

```bash
mvn clean install
```

### 3.2 运行测试

```bash
# 全部测试
mvn clean test

# 仅测试某个模块
mvn clean test -pl netty-rpc-domain
mvn clean test -pl netty-rpc-infrastructure -am
mvn clean test -pl netty-rpc-application -am
```

### 3.3 覆盖率报告

```bash
mvn clean test jacoco:report
# 各模块报告路径: {module}/target/site/jacoco/index.html
```

JaCoCo 已配置 `check` goal，每个模块的**行覆盖率低于 80%** 会直接导致构建失败。

### 3.4 跳过覆盖率检查 (临时)

```bash
mvn clean test -Djacoco.skip=true
```

---

## 4. TDD 开发工作流

项目遵循 Red-Green-Refactor 循环：

```
1. Red    → 编写一个将会失败的测试（明确预期行为）
2. Green  → 编写最简代码使测试通过
3. Refactor → 在所有测试保持绿色的前提下重构代码
```

### 测试分层

| 层级 | 测试方式 | 示例 |
|------|---------|------|
| Domain | 纯单元测试 | `RpcRequestTest` 验证模型构造、equals/hashCode |
| Infrastructure | 组件测试 | `RpcCodecTest` 使用 `EmbeddedChannel` 验证编解码 |
| Infrastructure | 集成测试 | `ZkRegistryTest` 使用 `TestingServer` 验证 ZK 交互 |
| Application | 单元测试 | `RpcServerHandlerTest` 验证反射调用逻辑 |
| Application | 端到端测试 | `IntegrationTest` 启动完整 Server + Client 验证调用链 |

---

## 5. 扩展开发指南

### 5.1 新增序列化方式

1. 在 `netty-rpc-infrastructure` 中实现 `Serializer` 接口：

```java
package com.xujn.nettyrpc.infrastructure.serialization;

public class GsonSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj) { /* Gson logic */ }
    @Override
    public Object deserialize(byte[] data) { /* Gson logic */ }
}
```

2. 在 `SerializationType` 枚举中新增类型码
3. 在 `RpcServer` / `NettyClient` 构造时注入新的 Serializer 实例

### 5.2 新增负载均衡策略

1. 实现 `LoadBalancer` 接口：

```java
public class WeightedRandomLoadBalancer implements LoadBalancer {
    @Override
    public String select(List<String> serviceAddresses) { /* 权重逻辑 */ }
}
```

2. 在创建 `RpcClientProxy` 时替换为新策略

### 5.3 新增 RPC 服务

实现业务接口并在 Server 端注册即可，无需修改框架代码。详见使用指南。

---

## 6. 关键设计取舍

### 6.1 当前实现边界

| 决策 | 当前方案 | 取舍原因 |
|------|---------|---------|
| 序列化 | Protostuff (Protobuf) | 高性能、小体积，兼容原 JDK 序列化（已实现双端支持） |
| 连接管理 | 简单 Channel 缓存 | 复杂连接池在初版不必要，`Map<address, Channel>` 已满足 |
| 服务发现 | 每次直连 ZK | 高频场景需加本地缓存；`ServiceDiscovery` 接口已隔离 |
| 注册注入 | 注解扫描 `@RpcService` | 通过 `RpcBootstrap` 实现了包扫描与代理注入，大幅度提升了易用性 |

### 6.2 演进方向

| 方向 | 说明 |
|------|------|
| 心跳保活 | 使用 `IdleStateHandler` 检测空闲连接并发送 Heartbeat |
| 连接池 | 引入 Netty `ChannelPool` 或 `FixedChannelPool` 管理长连接 |
| 本地路由缓存 | 使用 `CuratorCache` 监听 ZK 节点变更，缓存地址到内存 |
| 重试机制 | 在 `RpcClientProxy` 中增加可配置的重试策略 |
| SPI 扩展 | 使用 `ServiceLoader` 自动发现序列化器、负载均衡器实现 |
| 异步 RPC | 返回 `CompletableFuture<T>` 而非同步阻塞 |
