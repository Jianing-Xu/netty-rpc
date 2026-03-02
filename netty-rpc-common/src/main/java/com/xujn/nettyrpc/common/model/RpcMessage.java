package com.xujn.nettyrpc.common.model;

/**
 * Aggregate object representing a complete RPC protocol message,
 * combining the binary protocol header with a typed body payload.
 */
public class RpcMessage {

    private RpcProtocolHeader header;
    private Object body;

    public RpcMessage() {
    }

    public RpcMessage(RpcProtocolHeader header, Object body) {
        this.header = header;
        this.body = body;
    }

    /**
     * Factory: build an RpcMessage wrapping an RpcRequest.
     */
    public static RpcMessage buildRequest(RpcRequest request) {
        RpcProtocolHeader header = new RpcProtocolHeader(
                ProtocolConstants.MAGIC_NUMBER,
                ProtocolConstants.VERSION,
                MessageType.REQUEST.getCode(),
                SerializationType.JDK.getCode(),
                (byte) 0,
                request.getRequestId(),
                0, // bodyLength will be set by encoder after serialization
                0  // crc32 will be set by encoder after serialization
        );
        return new RpcMessage(header, request);
    }

    /**
     * Factory: build an RpcMessage wrapping an RpcResponse.
     */
    public static RpcMessage buildResponse(RpcResponse response) {
        RpcProtocolHeader header = new RpcProtocolHeader(
                ProtocolConstants.MAGIC_NUMBER,
                ProtocolConstants.VERSION,
                MessageType.RESPONSE.getCode(),
                SerializationType.JDK.getCode(),
                response.getStatus(),
                response.getRequestId(),
                0, // bodyLength
                0  // crc32
        );
        return new RpcMessage(header, response);
    }

    public RpcProtocolHeader getHeader() {
        return header;
    }

    public void setHeader(RpcProtocolHeader header) {
        this.header = header;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "RpcMessage{header=" + header + ", body=" + body + '}';
    }
}
