package com.xujn.nettyrpc.core.server;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.common.model.RpcRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RpcServerHandler's reflection-based service invocation.
 */
class RpcServerHandlerTest {

    // Test service interface and implementation
    public interface GreetService {
        String greet(String name);
        int add(int a, int b);
        void doNothing();
    }

    public static class GreetServiceImpl implements GreetService {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }

        @Override
        public int add(int a, int b) {
            return a + b;
        }

        @Override
        public void doNothing() {
            // no-op
        }
    }

    public static class FailingService {
        public String fail() {
            throw new RuntimeException("intentional failure");
        }
    }

    private RpcServerHandler handler;
    private Map<String, Object> serviceMap;

    @BeforeEach
    void setUp() {
        serviceMap = new HashMap<>();
        serviceMap.put(GreetService.class.getName(), new GreetServiceImpl());
        serviceMap.put(FailingService.class.getName(), new FailingService());
        handler = new RpcServerHandler(serviceMap);
    }

    @Test
    void testInvokeGreet() throws Exception {
        RpcRequest request = new RpcRequest(1L,
                GreetService.class.getName(), "greet",
                new Class<?>[]{String.class}, new Object[]{"World"});

        Object result = handler.invokeService(request);
        assertEquals("Hello, World", result);
    }

    @Test
    void testInvokeAdd() throws Exception {
        RpcRequest request = new RpcRequest(2L,
                GreetService.class.getName(), "add",
                new Class<?>[]{int.class, int.class}, new Object[]{3, 5});

        Object result = handler.invokeService(request);
        assertEquals(8, result);
    }

    @Test
    void testInvokeVoidMethod() throws Exception {
        RpcRequest request = new RpcRequest(3L,
                GreetService.class.getName(), "doNothing",
                new Class<?>[]{}, new Object[]{});

        Object result = handler.invokeService(request);
        assertNull(result);
    }

    @Test
    void testInvokeServiceNotFound() {
        RpcRequest request = new RpcRequest(4L,
                "com.nonexistent.Service", "method",
                new Class<?>[]{}, new Object[]{});

        RpcException ex = assertThrows(RpcException.class,
                () -> handler.invokeService(request));
        assertTrue(ex.getMessage().contains("Service not found"));
    }

    @Test
    void testInvokeMethodNotFound() {
        RpcRequest request = new RpcRequest(5L,
                GreetService.class.getName(), "nonExistentMethod",
                new Class<?>[]{}, new Object[]{});

        assertThrows(NoSuchMethodException.class,
                () -> handler.invokeService(request));
    }

    @Test
    void testInvokeFailingService() {
        RpcRequest request = new RpcRequest(6L,
                FailingService.class.getName(), "fail",
                new Class<?>[]{}, new Object[]{});

        assertThrows(Exception.class, () -> handler.invokeService(request));
    }
}
