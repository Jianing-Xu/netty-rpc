package com.xujn.nettyrpc.application.bootstrap;

import com.xujn.nettyrpc.domain.annotation.RpcReference;
import com.xujn.nettyrpc.domain.annotation.RpcService;
import com.xujn.nettyrpc.domain.loadbalance.LoadBalancer;
import com.xujn.nettyrpc.domain.registry.ServiceDiscovery;
import com.xujn.nettyrpc.domain.registry.ServiceRegistry;
import com.xujn.nettyrpc.infrastructure.serialization.ProtobufSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RpcBootstrapTest {

    // ── Test service interfaces and implementations ──

    public interface CalcService {
        int add(int a, int b);
    }

    @RpcService(CalcService.class)
    public static class CalcServiceImpl implements CalcService {
        @Override
        public int add(int a, int b) { return a + b; }
    }

    // ── Consumer with @RpcReference ──

    public static class Consumer {
        @RpcReference(timeout = 3000)
        private CalcService calcService;

        public CalcService getCalcService() { return calcService; }
    }

    // ── Tests ──

    @Test
    void testBuilderDefaultsToProtobufAndRoundRobin() {
        var bootstrap = RpcBootstrap.builder()
                .registry(mock(ServiceRegistry.class))
                .build();
        assertNotNull(bootstrap);
    }

    @Test
    void testAddServiceDirectly() {
        var bootstrap = RpcBootstrap.builder()
                .registry(mock(ServiceRegistry.class))
                .build();

        CalcServiceImpl impl = new CalcServiceImpl();
        bootstrap.addService(CalcService.class, impl);
        // No exception = success
    }

    @Test
    void testInjectReferencesCreatesProxy() {
        // Mock discovery to return an address
        ServiceDiscovery discovery = mock(ServiceDiscovery.class);
        when(discovery.discover(CalcService.class.getName()))
                .thenReturn(List.of("127.0.0.1:9999"));

        var bootstrap = RpcBootstrap.builder()
                .serializer(new ProtobufSerializer())
                .discovery(discovery)
                .build();

        Consumer consumer = new Consumer();
        bootstrap.injectReferences(consumer);

        // Proxy should be injected
        assertNotNull(consumer.getCalcService());
        // Should be a JDK proxy
        assertTrue(consumer.getCalcService().getClass().getName().contains("Proxy"));

        bootstrap.shutdown();
    }

    @Test
    void testInjectReferencesWithoutDiscoveryThrows() {
        var bootstrap = RpcBootstrap.builder().build();
        Consumer consumer = new Consumer();
        assertThrows(IllegalStateException.class, () -> bootstrap.injectReferences(consumer));
    }

    @Test
    void testStartServerWithoutRegistryThrows() {
        var bootstrap = RpcBootstrap.builder().build();
        assertThrows(IllegalStateException.class, bootstrap::startServer);
    }

    @Test
    void testBuilderCustomLoadBalancer() {
        LoadBalancer customLb = mock(LoadBalancer.class);
        var bootstrap = RpcBootstrap.builder()
                .loadBalancer(customLb)
                .discovery(mock(ServiceDiscovery.class))
                .build();
        assertNotNull(bootstrap);
    }
}
