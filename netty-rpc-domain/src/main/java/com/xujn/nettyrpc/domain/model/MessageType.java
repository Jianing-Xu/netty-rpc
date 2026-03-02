package com.xujn.nettyrpc.domain.model;

/**
 * Enumeration of message types in the RPC protocol.
 */
public enum MessageType {

    REQUEST((byte) 0),
    RESPONSE((byte) 1),
    HEARTBEAT((byte) 2);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
