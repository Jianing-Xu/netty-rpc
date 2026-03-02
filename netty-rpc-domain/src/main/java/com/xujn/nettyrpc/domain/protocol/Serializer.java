package com.xujn.nettyrpc.domain.protocol;

/**
 * Abstraction for object serialization/deserialization.
 * Infrastructure layer provides concrete implementations (e.g. JDK native).
 */
public interface Serializer {

    /**
     * Serialize an object to a byte array.
     */
    byte[] serialize(Object obj);

    /**
     * Deserialize a byte array back to an object.
     */
    Object deserialize(byte[] data);
}
