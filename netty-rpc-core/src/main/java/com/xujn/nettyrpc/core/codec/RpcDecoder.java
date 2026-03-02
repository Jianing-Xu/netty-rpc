package com.xujn.nettyrpc.core.codec;

import com.xujn.nettyrpc.common.model.MessageType;
import com.xujn.nettyrpc.common.model.ProtocolConstants;
import com.xujn.nettyrpc.common.model.RpcMessage;
import com.xujn.nettyrpc.common.model.RpcProtocolHeader;
import com.xujn.nettyrpc.api.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decodes the custom binary protocol frame back into an {@link RpcMessage}.
 * Handles TCP sticky/half-packet by checking readable bytes against header and body length.
 */
public class RpcDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(RpcDecoder.class);

    private final Serializer serializer;

    public RpcDecoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < ProtocolConstants.HEADER_LENGTH) {
            return;
        }

        in.markReaderIndex();

        short magic = in.readShort();
        if (magic != ProtocolConstants.MAGIC_NUMBER) {
            in.resetReaderIndex();
            throw new IllegalArgumentException(
                    "Invalid magic number: 0x" + Integer.toHexString(magic & 0xFFFF));
        }

        byte version = in.readByte();
        byte messageType = in.readByte();
        byte serializationType = in.readByte();
        byte status = in.readByte();
        long requestId = in.readLong();
        int bodyLength = in.readInt();

        // Half-packet: body not fully received yet
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] bodyBytes = new byte[bodyLength];
        in.readBytes(bodyBytes);

        Object body = serializer.deserialize(bodyBytes);

        RpcProtocolHeader header = new RpcProtocolHeader(
                magic, version, messageType, serializationType,
                status, requestId, bodyLength);

        RpcMessage message = new RpcMessage(header, body);
        out.add(message);

        log.debug("Decoded message: type={}, requestId={}, bodyLength={}",
                MessageType.fromCode(messageType), requestId, bodyLength);
    }
}
