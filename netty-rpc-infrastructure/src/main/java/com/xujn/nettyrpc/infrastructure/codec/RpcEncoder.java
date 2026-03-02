package com.xujn.nettyrpc.infrastructure.codec;

import com.xujn.nettyrpc.domain.model.RpcMessage;
import com.xujn.nettyrpc.domain.protocol.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes an {@link RpcMessage} into the custom binary protocol frame.
 *
 * Frame layout (18-byte header + variable body):
 * | Magic(2) | Version(1) | MsgType(1) | SerType(1) | Status(1) | RequestId(8) | BodyLen(4) | Body(N) |
 */
public class RpcEncoder extends MessageToByteEncoder<RpcMessage> {

    private final Serializer serializer;

    public RpcEncoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        var header = msg.getHeader();
        byte[] bodyBytes = serializer.serialize(msg.getBody());

        out.writeShort(header.getMagic());
        out.writeByte(header.getVersion());
        out.writeByte(header.getMessageType());
        out.writeByte(header.getSerializationType());
        out.writeByte(header.getStatus());
        out.writeLong(header.getRequestId());
        out.writeInt(bodyBytes.length);
        out.writeBytes(bodyBytes);
    }
}
