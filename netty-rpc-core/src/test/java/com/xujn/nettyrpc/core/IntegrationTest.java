package com.xujn.nettyrpc.core;

import com.xujn.nettyrpc.core.client.NettyClient;
import com.xujn.nettyrpc.core.client.RpcClientProxy;
import com.xujn.nettyrpc.core.server.RpcServer;
import com.xujn.nettyrpc.api.registry.ServiceDiscovery;
import com.xujn.nettyrpc.api.registry.ServiceRegistry;
import com.xujn.nettyrpc.core.loadbalance.RoundRobinLoadBalancer;
import com.xujn.nettyrpc.registry.zk.ZkServiceDiscovery;
import com.xujn.nettyrpc.registry.zk.ZkServiceRegistry;
import com.xujn.nettyrpc.core.serialization.ProtobufSerializer;
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
        java.util.concurrent.CompletableFuture<String> sayHelloAsync(String name);
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
        @Override
        public java.util.concurrent.CompletableFuture<String> sayHelloAsync(String name) {
            // Server simply returns the value, RPC framework client will wrap to future!
            // Note: Currently, our reflection simply delegates to this method. 
            // In a fully reactive server, this would run differently, but for testing
            // we simulate a business method returning a CompletableFuture.
            return java.util.concurrent.CompletableFuture.completedFuture("Hello Async, " + name + "!");
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
                discovery, new RoundRobinLoadBalancer(), nettyClient, 5000, 3);

        HelloService helloService = proxy.create(HelloService.class);

        String result = helloService.sayHello("Netty");
        assertEquals("Hello, Netty!", result);
    }

    @Test
    void testEndToEndAddition() {
        ServiceDiscovery discovery = new ZkServiceDiscovery(zkClient);
        RpcClientProxy proxy = new RpcClientProxy(
                discovery, new RoundRobinLoadBalancer(), nettyClient, 5000, 0);

        HelloService helloService = proxy.create(HelloService.class);

        int result = helloService.add(10, 20);
        assertEquals(30, result);
    }

    @Test
    void testMultipleCallsOnSameProxy() {
        ServiceDiscovery discovery = new ZkServiceDiscovery(zkClient);
        RpcClientProxy proxy = new RpcClientProxy(
                discovery, new RoundRobinLoadBalancer(), nettyClient, 5000, 0);

        HelloService helloService = proxy.create(HelloService.class);

        for (int i = 0; i < 5; i++) {
            assertEquals("Hello, User" + i + "!", helloService.sayHello("User" + i));
        }
    }

    @Test
    void testAsyncCall() throws Exception {
        ServiceDiscovery discovery = new ZkServiceDiscovery(zkClient);
        RpcClientProxy proxy = new RpcClientProxy(
                discovery, new RoundRobinLoadBalancer(), nettyClient, 5000, 3);

        HelloService helloService = proxy.create(HelloService.class);

        java.util.concurrent.CompletableFuture<String> future = helloService.sayHelloAsync("Netty Async");
        String result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("Hello Async, Netty Async!", result);
    }
}
