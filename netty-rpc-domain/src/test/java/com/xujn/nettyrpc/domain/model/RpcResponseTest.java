package com.xujn.nettyrpc.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RpcResponseTest {

    @Test
    void testSuccessFactory() {
        RpcResponse response = RpcResponse.success(1L, "hello");
        assertEquals(1L, response.getRequestId());
        assertEquals((byte) 0, response.getStatus());
        assertEquals("hello", response.getResult());
        assertNull(response.getError());
        assertTrue(response.isSuccess());
    }

    @Test
    void testErrorFactory() {
        RuntimeException ex = new RuntimeException("fail");
        RpcResponse response = RpcResponse.error(2L, ex);
        assertEquals(2L, response.getRequestId());
        assertEquals((byte) 1, response.getStatus());
        assertNull(response.getResult());
        assertEquals(ex, response.getError());
        assertFalse(response.isSuccess());
    }

    @Test
    void testSetters() {
        RpcResponse response = new RpcResponse();
        response.setRequestId(10L);
        response.setStatus((byte) 0);
        response.setResult(42);
        response.setError(null);

        assertEquals(10L, response.getRequestId());
        assertEquals((byte) 0, response.getStatus());
        assertEquals(42, response.getResult());
        assertNull(response.getError());
    }

    @Test
    void testEqualsAndHashCode() {
        RpcResponse a = RpcResponse.success(1L, "x");
        RpcResponse b = RpcResponse.success(1L, "y");
        RpcResponse c = RpcResponse.success(2L, "x");

        assertEquals(a, b);  // same requestId and status
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void testEqualsEdgeCases() {
        RpcResponse a = RpcResponse.success(1L, "x");
        assertNotEquals(a, null);
        assertNotEquals(a, "string");
        assertEquals(a, a);
    }

    @Test
    void testToString() {
        RpcResponse resp = RpcResponse.success(1L, "result");
        String str = resp.toString();
        assertTrue(str.contains("requestId=1"));
        assertTrue(str.contains("result"));
    }
}
