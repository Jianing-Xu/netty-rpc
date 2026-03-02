package com.xujn.nettyrpc.application;

import com.xujn.nettyrpc.application.client.NettyClient;
import com.xujn.nettyrpc.application.client.RpcClientProxy;
import com.xujn.nettyrpc.application.server.RpcServer;
import com.xujn.nettyrpc.domain.registry.ServiceDiscovery;
import com.xujn.nettyrpc.domain.registry.ServiceRegistry;
import com.xujn.nettyrpc.infrastructure.loadbalance.RoundRobinLoadBalancer;
import com.xujn.nettyrpc.infrastructure.registry.ZkServiceDiscovery;
import com.xujn.nettyrpc.infrastructure.registry.ZkServiceRegistry;
import com.xujn.nettyrpc.infrastructure.serialization.ProtobufSerializer;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test: starts ZK + RPC server, creates client proxy, invokes remote method.
 */
class IntegrationTest {

    public interface HelloService {
        String sayHello(String name);
        int add(int a, int b);
    }

    public static class HelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello, " + name + "!";
        }
        @Override
        public int add(int a, int b) {
            return a + b;
        }
    }

    private static TestingServer zkServer;
    private CuratorFramework zkClient;
    private RpcServer rpcServer;
    private NettyClient nettyClient;

    @BeforeAll
    static void startZk() throws Exception {
        zkServer = new TestingServer(true);
    }

    @AfterAll
    static void stopZk() throws Exception {
        if (zkServer != null) zkServer.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        zkClient = CuratorFrameworkFactory.builder()
                .connectString(zkServer.getConnectString())
                .sessionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(500, 3))
                .build();
        zkClient.start();

        var serializer = new ProtobufSerializer();
        ServiceRegistry registry = new ZkServiceRegistry(zkClient);

        rpcServer = new RpcServer("127.0.0.1", 18888, registry, serializer);
        rpcServer.addService(HelloService.class.getName(), new HelloServiceImpl());
        rpcServer.start();

        nettyClient = new NettyClient(serializer);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (nettyClient != null) nettyClient.shutdown();
        if (rpcServer != null) rpcServer.shutdown();
        // Clean up ZK
        try {
            if (zkClient.checkExists().forPath("/netty-rpc") != null) {
                zkClient.delete().deletingChildrenIfNeeded().forPath("/netty-rpc");
            }
        } catch (Exception ignored) {}
        zkClient.close();
    }

    @Test
    void testEndToEndRpcCall() {
        ServiceDiscovery discovery = new ZkServiceDiscovery(zkClient);
        RpcClientProxy proxy = new RpcClientProxy(
                discovery, new RoundRobinLoadBalancer(), nettyClient, 5000);

        HelloService helloService = proxy.create(HelloService.class);

        String result = helloService.sayHello("Netty");
        assertEquals("Hello, Netty!", result);
    }

    @Test
    void testEndToEndAddition() {
        ServiceDiscovery discovery = new ZkServiceDiscovery(zkClient);
        RpcClientProxy proxy = new RpcClientProxy(
                discovery, new RoundRobinLoadBalancer(), nettyClient, 5000);

        HelloService helloService = proxy.create(HelloService.class);

        int result = helloService.add(10, 20);
        assertEquals(30, result);
    }

    @Test
    void testMultipleCallsOnSameProxy() {
        ServiceDiscovery discovery = new ZkServiceDiscovery(zkClient);
        RpcClientProxy proxy = new RpcClientProxy(
                discovery, new RoundRobinLoadBalancer(), nettyClient, 5000);

        HelloService helloService = proxy.create(HelloService.class);

        for (int i = 0; i < 5; i++) {
            assertEquals("Hello, User" + i + "!", helloService.sayHello("User" + i));
        }
    }
}
