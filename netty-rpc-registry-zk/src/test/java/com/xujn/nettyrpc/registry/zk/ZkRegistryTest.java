package com.xujn.nettyrpc.registry.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZkRegistryTest {

    private static TestingServer zkServer;
    private CuratorFramework zkClient;
    private ZkServiceRegistry registry;
    private ZkServiceDiscovery discovery;

    @BeforeAll
    static void startZk() throws Exception {
        zkServer = new TestingServer(true);
    }

    @AfterAll
    static void stopZk() throws Exception {
        if (zkServer != null) {
            zkServer.close();
        }
    }

    @BeforeEach
    void setUp() {
        zkClient = CuratorFrameworkFactory.builder()
                .connectString(zkServer.getConnectString())
                .sessionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(500, 3))
                .build();
        zkClient.start();
        registry = new ZkServiceRegistry(zkClient);
        discovery = new ZkServiceDiscovery(zkClient);
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        try {
            if (zkClient.checkExists().forPath("/netty-rpc") != null) {
                zkClient.delete().deletingChildrenIfNeeded().forPath("/netty-rpc");
            }
        } catch (Exception ignored) {
        }
        zkClient.close();
    }

    @Test
    void testRegisterAndDiscover() {
        registry.register("com.example.HelloService", "127.0.0.1:8080");

        List<String> addresses = discovery.discover("com.example.HelloService");
        assertEquals(1, addresses.size());
        assertEquals("127.0.0.1:8080", addresses.get(0));
    }

    @Test
    void testRegisterMultipleInstances() {
        registry.register("com.example.HelloService", "127.0.0.1:8080");
        registry.register("com.example.HelloService", "127.0.0.1:8081");

        List<String> addresses = discovery.discover("com.example.HelloService");
        assertEquals(2, addresses.size());
        assertTrue(addresses.contains("127.0.0.1:8080"));
        assertTrue(addresses.contains("127.0.0.1:8081"));
    }

    @Test
    void testUnregister() {
        registry.register("com.example.HelloService", "127.0.0.1:8080");
        registry.unregister("com.example.HelloService", "127.0.0.1:8080");

        List<String> addresses = discovery.discover("com.example.HelloService");
        assertTrue(addresses.isEmpty());
    }

    @Test
    void testDiscoverNonExistentService() {
        List<String> addresses = discovery.discover("com.example.Unknown");
        assertTrue(addresses.isEmpty());
    }

    @Test
    void testRegisterDuplicateAddress() {
        registry.register("com.example.Svc", "127.0.0.1:8080");
        registry.register("com.example.Svc", "127.0.0.1:8080");

        List<String> addresses = discovery.discover("com.example.Svc");
        assertEquals(1, addresses.size());
    }

    @Test
    void testUnregisterNonExistent() {
        // Should not throw
        assertDoesNotThrow(() ->
                registry.unregister("com.example.Svc", "127.0.0.1:9999"));
    }

    @Test
    void testMultipleServices() {
        registry.register("com.example.SvcA", "127.0.0.1:8080");
        registry.register("com.example.SvcB", "127.0.0.1:9090");

        assertEquals(1, discovery.discover("com.example.SvcA").size());
        assertEquals(1, discovery.discover("com.example.SvcB").size());
    }
}
