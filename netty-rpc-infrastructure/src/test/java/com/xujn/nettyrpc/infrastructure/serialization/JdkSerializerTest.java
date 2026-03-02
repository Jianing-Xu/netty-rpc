package com.xujn.nettyrpc.infrastructure.serialization;

import com.xujn.nettyrpc.domain.exception.RpcException;
import com.xujn.nettyrpc.domain.model.RpcRequest;
import com.xujn.nettyrpc.domain.model.RpcResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdkSerializerTest {

    private JdkSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JdkSerializer();
    }

    @Test
    void testSerializeAndDeserializeRequest() {
        RpcRequest request = new RpcRequest(1L, "com.example.Svc", "hello",
                new Class<?>[]{String.class}, new Object[]{"world"});

        byte[] bytes = serializer.serialize(request);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        Object result = serializer.deserialize(bytes);
        assertTrue(result instanceof RpcRequest);
        RpcRequest deserialized = (RpcRequest) result;
        assertEquals(request.getRequestId(), deserialized.getRequestId());
        assertEquals(request.getClassName(), deserialized.getClassName());
        assertEquals(request.getMethodName(), deserialized.getMethodName());
    }

    @Test
    void testSerializeAndDeserializeResponse() {
        RpcResponse response = RpcResponse.success(2L, "result_value");

        byte[] bytes = serializer.serialize(response);
        Object result = serializer.deserialize(bytes);

        assertTrue(result instanceof RpcResponse);
        RpcResponse deserialized = (RpcResponse) result;
        assertEquals(2L, deserialized.getRequestId());
        assertTrue(deserialized.isSuccess());
        assertEquals("result_value", deserialized.getResult());
    }

    @Test
    void testSerializeAndDeserializeErrorResponse() {
        RpcResponse response = RpcResponse.error(3L, new RuntimeException("oops"));

        byte[] bytes = serializer.serialize(response);
        RpcResponse deserialized = (RpcResponse) serializer.deserialize(bytes);

        assertFalse(deserialized.isSuccess());
        assertNotNull(deserialized.getError());
        assertEquals("oops", deserialized.getError().getMessage());
    }

    @Test
    void testSerializeNull() {
        assertThrows(RpcException.class, () -> serializer.serialize(null));
    }

    @Test
    void testDeserializeNull() {
        assertThrows(RpcException.class, () -> serializer.deserialize(null));
    }

    @Test
    void testDeserializeEmptyArray() {
        assertThrows(RpcException.class, () -> serializer.deserialize(new byte[0]));
    }

    @Test
    void testDeserializeInvalidData() {
        assertThrows(RpcException.class, () -> serializer.deserialize(new byte[]{1, 2, 3}));
    }

    @Test
    void testSerializeString() {
        byte[] bytes = serializer.serialize("hello");
        Object result = serializer.deserialize(bytes);
        assertEquals("hello", result);
    }

    @Test
    void testSerializeInteger() {
        byte[] bytes = serializer.serialize(42);
        Object result = serializer.deserialize(bytes);
        assertEquals(42, result);
    }
}
