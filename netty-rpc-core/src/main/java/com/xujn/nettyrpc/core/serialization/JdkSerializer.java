package com.xujn.nettyrpc.core.serialization;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.api.serialization.Serializer;

import java.io.*;

/**
 * JDK native serialization implementation.
 * Uses ObjectOutputStream/ObjectInputStream for object marshalling.
 */
public class JdkSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            throw new RpcException("Cannot serialize null object");
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RpcException("JDK serialization failed", e);
        }
    }

    @Override
    public Object deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            throw new RpcException("Cannot deserialize null or empty data");
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RpcException("JDK deserialization failed", e);
        }
    }
}
