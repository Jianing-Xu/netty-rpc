package com.xujn.nettyrpc.common.model;

/**
 * Protocol header for the custom binary RPC protocol.
 * Total header size: 22 bytes.
 *
 * Layout:
 * | Magic(2) | Version(1) | MsgType(1) | SerType(1) | Status(1) | RequestId(8) | BodyLen(4) | CRC32(4) |
 */
public class RpcProtocolHeader {

    private short magic;
    private byte version;
    private byte messageType;
    private byte serializationType;
    private byte status;
    private long requestId;
    private int bodyLength;
    private int crc32;

    public RpcProtocolHeader() {
    }

    public RpcProtocolHeader(short magic, byte version, byte messageType,
                             byte serializationType, byte status, long requestId, int bodyLength, int crc32) {
        this.magic = magic;
        this.version = version;
        this.messageType = messageType;
        this.serializationType = serializationType;
        this.status = status;
        this.requestId = requestId;
        this.bodyLength = bodyLength;
        this.crc32 = crc32;
    }

    public short getMagic() { return magic; }
    public void setMagic(short magic) { this.magic = magic; }

    public byte getVersion() { return version; }
    public void setVersion(byte version) { this.version = version; }

    public byte getMessageType() { return messageType; }
    public void setMessageType(byte messageType) { this.messageType = messageType; }

    public byte getSerializationType() { return serializationType; }
    public void setSerializationType(byte serializationType) { this.serializationType = serializationType; }

    public byte getStatus() { return status; }
    public void setStatus(byte status) { this.status = status; }

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }

    public int getBodyLength() { return bodyLength; }
    public void setBodyLength(int bodyLength) { this.bodyLength = bodyLength; }

    public int getCrc32() { return crc32; }
    public void setCrc32(int crc32) { this.crc32 = crc32; }

    @Override
    public String toString() {
        return "RpcProtocolHeader{" +
                "magic=0x" + Integer.toHexString(magic & 0xFFFF) +
                ", version=" + version +
                ", messageType=" + messageType +
                ", serializationType=" + serializationType +
                ", status=" + status +
                ", requestId=" + requestId +
                ", bodyLength=" + bodyLength +
                ", crc32=" + crc32 +
                '}';
    }
}
