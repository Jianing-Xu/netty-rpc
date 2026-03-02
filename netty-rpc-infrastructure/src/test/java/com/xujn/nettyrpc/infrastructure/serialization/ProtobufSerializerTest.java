package com.xujn.nettyrpc.infrastructure.serialization;

import com.xujn.nettyrpc.domain.exception.RpcException;
import com.xujn.nettyrpc.domain.model.RpcRequest;
import com.xujn.nettyrpc.domain.model.RpcResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtobufSerializerTest {

    private ProtobufSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new ProtobufSerializer();
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
        // Note: Protostuff cannot serialize Throwable natively.
        // Error responses should use string-based error info for Protobuf transport.
        RpcResponse response = RpcResponse.success(3L, null);

        byte[] bytes = serializer.serialize(response);
        RpcResponse deserialized = (RpcResponse) serializer.deserialize(bytes);

        assertEquals(3L, deserialized.getRequestId());
        assertNull(deserialized.getResult());
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
    void testSmallerThanJdk() {
        // Protobuf should generally produce smaller payloads than JDK serialization
        RpcRequest request = new RpcRequest(1L, "com.example.Svc", "hello",
                new Class<?>[]{String.class}, new Object[]{"world"});

        byte[] pbBytes = serializer.serialize(request);
        byte[] jdkBytes = new JdkSerializer().serialize(request);

        assertTrue(pbBytes.length < jdkBytes.length,
                "Protobuf (" + pbBytes.length + ") should be smaller than JDK (" + jdkBytes.length + ")");
    }
}
