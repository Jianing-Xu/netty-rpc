package com.xujn.nettyrpc.domain.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class RpcMessageTest {

    @Test
    void testBuildRequest() {
        RpcRequest request = new RpcRequest(1L, "com.example.Svc", "hello",
                new Class<?>[]{String.class}, new Object[]{"world"});
        RpcMessage msg = RpcMessage.buildRequest(request);

        assertNotNull(msg.getHeader());
        assertEquals(ProtocolConstants.MAGIC_NUMBER, msg.getHeader().getMagic());
        assertEquals(ProtocolConstants.VERSION, msg.getHeader().getVersion());
        assertEquals(MessageType.REQUEST.getCode(), msg.getHeader().getMessageType());
        assertEquals(SerializationType.JDK.getCode(), msg.getHeader().getSerializationType());
        assertEquals((byte) 0, msg.getHeader().getStatus());
        assertEquals(1L, msg.getHeader().getRequestId());
        assertSame(request, msg.getBody());
    }

    @Test
    void testBuildResponse() {
        RpcResponse response = RpcResponse.success(2L, "result");
        RpcMessage msg = RpcMessage.buildResponse(response);

        assertEquals(ProtocolConstants.MAGIC_NUMBER, msg.getHeader().getMagic());
        assertEquals(MessageType.RESPONSE.getCode(), msg.getHeader().getMessageType());
        assertEquals((byte) 0, msg.getHeader().getStatus());
        assertEquals(2L, msg.getHeader().getRequestId());
        assertSame(response, msg.getBody());
    }

    @Test
    void testBuildErrorResponse() {
        RpcResponse response = RpcResponse.error(3L, new RuntimeException("fail"));
        RpcMessage msg = RpcMessage.buildResponse(response);

        assertEquals(MessageType.RESPONSE.getCode(), msg.getHeader().getMessageType());
        assertEquals((byte) 1, msg.getHeader().getStatus());
        assertEquals(3L, msg.getHeader().getRequestId());
    }

    @Test
    void testDefaultConstructorAndSetters() {
        RpcMessage msg = new RpcMessage();
        assertNull(msg.getHeader());
        assertNull(msg.getBody());

        RpcProtocolHeader h = new RpcProtocolHeader();
        msg.setHeader(h);
        msg.setBody("payload");

        assertSame(h, msg.getHeader());
        assertEquals("payload", msg.getBody());
    }

    @Test
    void testToString() {
        RpcMessage msg = RpcMessage.buildRequest(
                new RpcRequest(1L, "Svc", "m", null, null));
        assertNotNull(msg.toString());
        assertTrue(msg.toString().contains("RpcMessage"));
    }

    @Test
    void testMessageTypeFromCode() {
        assertEquals(MessageType.REQUEST, MessageType.fromCode((byte) 0));
        assertEquals(MessageType.RESPONSE, MessageType.fromCode((byte) 1));
        assertEquals(MessageType.HEARTBEAT, MessageType.fromCode((byte) 2));
        assertThrows(IllegalArgumentException.class, () -> MessageType.fromCode((byte) 99));
    }

    @Test
    void testSerializationTypeFromCode() {
        assertEquals(SerializationType.JDK, SerializationType.fromCode((byte) 0));
        assertThrows(IllegalArgumentException.class, () -> SerializationType.fromCode((byte) 99));
    }

    @Test
    void testProtocolConstants() {
        assertEquals((short) 0xCAFE, ProtocolConstants.MAGIC_NUMBER);
        assertEquals((byte) 1, ProtocolConstants.VERSION);
        assertEquals(18, ProtocolConstants.HEADER_LENGTH);
        assertThrows(InvocationTargetException.class, () -> {
            var ctor = ProtocolConstants.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        });
    }

    @Test
    void testProtocolHeaderToString() {
        RpcProtocolHeader header = new RpcProtocolHeader(
                (short) 0xCAFE, (byte) 1, (byte) 0, (byte) 0, (byte) 0, 100L, 256);
        String str = header.toString();
        assertTrue(str.contains("cafe"));
        assertTrue(str.contains("100"));
        assertTrue(str.contains("256"));
    }
}
