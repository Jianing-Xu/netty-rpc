package com.xujn.nettyrpc.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RpcRequestTest {

    @Test
    void testDefaultConstruction() {
        RpcRequest request = new RpcRequest();
        assertEquals(0, request.getRequestId());
        assertNull(request.getClassName());
        assertNull(request.getMethodName());
        assertNull(request.getParameterTypes());
        assertNull(request.getParameters());
    }

    @Test
    void testFullConstruction() {
        RpcRequest request = new RpcRequest(
                1L, "com.example.Service", "hello",
                new Class<?>[]{String.class}, new Object[]{"world"});

        assertEquals(1L, request.getRequestId());
        assertEquals("com.example.Service", request.getClassName());
        assertEquals("hello", request.getMethodName());
        assertArrayEquals(new Class<?>[]{String.class}, request.getParameterTypes());
        assertArrayEquals(new Object[]{"world"}, request.getParameters());
    }

    @Test
    void testSetters() {
        RpcRequest request = new RpcRequest();
        request.setRequestId(42L);
        request.setClassName("com.example.Foo");
        request.setMethodName("bar");
        request.setParameterTypes(new Class<?>[]{int.class});
        request.setParameters(new Object[]{100});

        assertEquals(42L, request.getRequestId());
        assertEquals("com.example.Foo", request.getClassName());
        assertEquals("bar", request.getMethodName());
    }

    @Test
    void testEqualsAndHashCode() {
        RpcRequest a = new RpcRequest(1L, "Svc", "m", new Class<?>[]{String.class}, new Object[]{"x"});
        RpcRequest b = new RpcRequest(1L, "Svc", "m", new Class<?>[]{String.class}, new Object[]{"x"});
        RpcRequest c = new RpcRequest(2L, "Svc", "m", new Class<?>[]{String.class}, new Object[]{"x"});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void testEqualsWithNull() {
        RpcRequest a = new RpcRequest(1L, "Svc", "m", null, null);
        assertNotEquals(a, null);
        assertNotEquals(a, "not an RpcRequest");
        assertEquals(a, a);
    }

    @Test
    void testToString() {
        RpcRequest request = new RpcRequest(1L, "Svc", "hello", new Class<?>[]{}, new Object[]{});
        String str = request.toString();
        assertTrue(str.contains("requestId=1"));
        assertTrue(str.contains("Svc"));
        assertTrue(str.contains("hello"));
    }
}
