package com.xujn.nettyrpc.domain.model;

/**
 * Enumeration of serialization strategies supported by the protocol.
 */
public enum SerializationType {

    JDK((byte) 0);

    private final byte code;

    SerializationType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static SerializationType fromCode(byte code) {
        for (SerializationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown serialization type code: " + code);
    }
}
