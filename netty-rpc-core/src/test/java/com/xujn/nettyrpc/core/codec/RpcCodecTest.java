package com.xujn.nettyrpc.core.codec;

import com.xujn.nettyrpc.common.model.*;
import com.xujn.nettyrpc.api.serialization.Serializer;
import com.xujn.nettyrpc.core.serialization.JdkSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RpcCodecTest {

    private Serializer serializer;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        serializer = new JdkSerializer();
        channel = new EmbeddedChannel(new RpcDecoder(serializer), new RpcEncoder(serializer));
    }

    @Test
    void testEncodeDecodeRequest() {
        RpcRequest request = new RpcRequest(1L, "com.example.Svc", "hello",
                new Class<?>[]{String.class}, new Object[]{"world"});
        RpcMessage outMsg = RpcMessage.buildRequest(request);

        // Encode
        channel.writeOutbound(outMsg);
        ByteBuf encoded = channel.readOutbound();
        assertNotNull(encoded);

        // Decode
        channel.writeInbound(encoded);
        RpcMessage decoded = channel.readInbound();
        assertNotNull(decoded);

        assertEquals(ProtocolConstants.MAGIC_NUMBER, decoded.getHeader().getMagic());
        assertEquals(MessageType.REQUEST.getCode(), decoded.getHeader().getMessageType());
        assertEquals(1L, decoded.getHeader().getRequestId());

        assertTrue(decoded.getBody() instanceof RpcRequest);
        RpcRequest decodedReq = (RpcRequest) decoded.getBody();
        assertEquals("com.example.Svc", decodedReq.getClassName());
        assertEquals("hello", decodedReq.getMethodName());
    }

    @Test
    void testEncodeDecodeResponse() {
        RpcResponse response = RpcResponse.success(2L, "result");
        RpcMessage outMsg = RpcMessage.buildResponse(response);

        channel.writeOutbound(outMsg);
        ByteBuf encoded = channel.readOutbound();
        channel.writeInbound(encoded);
        RpcMessage decoded = channel.readInbound();

        assertNotNull(decoded);
        assertEquals(MessageType.RESPONSE.getCode(), decoded.getHeader().getMessageType());
        assertEquals((byte) 0, decoded.getHeader().getStatus());

        RpcResponse decodedResp = (RpcResponse) decoded.getBody();
        assertEquals(2L, decodedResp.getRequestId());
        assertEquals("result", decodedResp.getResult());
    }

    @Test
    void testEncodeDecodeErrorResponse() {
        RpcResponse response = RpcResponse.error(3L, new RuntimeException("fail"));
        RpcMessage outMsg = RpcMessage.buildResponse(response);

        channel.writeOutbound(outMsg);
        ByteBuf encoded = channel.readOutbound();
        channel.writeInbound(encoded);
        RpcMessage decoded = channel.readInbound();

        assertEquals((byte) 1, decoded.getHeader().getStatus());
        RpcResponse decodedResp = (RpcResponse) decoded.getBody();
        assertFalse(decodedResp.isSuccess());
        assertNotNull(decodedResp.getError());
    }

    @Test
    void testDecoderWaitsForFullHeader() {
        // Send only partial header (less than 18 bytes)
        ByteBuf partial = Unpooled.buffer(10);
        partial.writeShort(ProtocolConstants.MAGIC_NUMBER);
        partial.writeByte(1); // version
        partial.writeByte(0); // msgType
        // Only 4 bytes written, need 18 total

        channel.writeInbound(partial);
        RpcMessage decoded = channel.readInbound();
        assertNull(decoded); // Should not decode yet
    }

    @Test
    void testDecoderWaitsForFullBody() {
        // Write a complete header but incomplete body
        RpcRequest request = new RpcRequest(1L, "Svc", "m", null, null);
        byte[] body = serializer.serialize(request);

        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(ProtocolConstants.MAGIC_NUMBER);
        buf.writeByte(ProtocolConstants.VERSION);
        buf.writeByte(MessageType.REQUEST.getCode());
        buf.writeByte(SerializationType.JDK.getCode());
        buf.writeByte(0); // status
        buf.writeLong(1L); // requestId
        buf.writeInt(body.length); // bodyLength = full
        buf.writeBytes(body, 0, body.length / 2); // only half the body

        channel.writeInbound(buf);
        RpcMessage decoded = channel.readInbound();
        assertNull(decoded); // Should wait for remaining body bytes
    }

    @Test
    void testDecoderRejectsInvalidMagic() {
        ByteBuf bad = Unpooled.buffer(18);
        bad.writeShort(0xDEAD); // bad magic
        bad.writeByte(1);
        bad.writeByte(0);
        bad.writeByte(0);
        bad.writeByte(0);
        bad.writeLong(1L);
        bad.writeInt(0);

        assertThrows(io.netty.handler.codec.DecoderException.class, () -> channel.writeInbound(bad));
    }

    @Test
    void testMultipleMessagesInSequence() {
        for (int i = 0; i < 5; i++) {
            RpcRequest request = new RpcRequest(i, "Svc", "m" + i, null, null);
            RpcMessage msg = RpcMessage.buildRequest(request);
            channel.writeOutbound(msg);
            ByteBuf encoded = channel.readOutbound();
            channel.writeInbound(encoded);
            RpcMessage decoded = channel.readInbound();
            assertNotNull(decoded);
            assertEquals(i, decoded.getHeader().getRequestId());
        }
    }

    @Test
    void testHeaderFieldsPreserved() {
        RpcRequest request = new RpcRequest(999L, "Svc", "m", null, null);
        RpcMessage msg = RpcMessage.buildRequest(request);

        channel.writeOutbound(msg);
        ByteBuf encoded = channel.readOutbound();
        channel.writeInbound(encoded);
        RpcMessage decoded = channel.readInbound();

        RpcProtocolHeader h = decoded.getHeader();
        assertEquals(ProtocolConstants.MAGIC_NUMBER, h.getMagic());
        assertEquals(ProtocolConstants.VERSION, h.getVersion());
        assertEquals(SerializationType.JDK.getCode(), h.getSerializationType());
        assertEquals(999L, h.getRequestId());
        assertTrue(h.getBodyLength() > 0);
    }
}
