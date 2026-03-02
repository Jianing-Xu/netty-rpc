package com.xujn.nettyrpc.infrastructure.serialization;

import com.xujn.nettyrpc.domain.exception.RpcException;
import com.xujn.nettyrpc.domain.protocol.Serializer;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protobuf-compatible serializer using Protostuff runtime.
 * Supports serialization of arbitrary Java POJOs without pre-defined .proto files.
 *
 * Uses a wrapper class to handle all object types uniformly,
 * caching schemas for performance.
 */
public class ProtobufSerializer implements Serializer {

    @SuppressWarnings("rawtypes")
    private static final Map<Class<?>, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            throw new RpcException("Cannot serialize null object");
        }
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
        try {
            ObjectWrapper wrapper = new ObjectWrapper(obj);
            Schema<ObjectWrapper> schema = getSchema(ObjectWrapper.class);
            return ProtostuffIOUtil.toByteArray(wrapper, schema, buffer);
        } catch (Exception e) {
            throw new RpcException("Protobuf serialization failed", e);
        } finally {
            buffer.clear();
        }
    }

    @Override
    public Object deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            throw new RpcException("Cannot deserialize null or empty data");
        }
        try {
            Schema<ObjectWrapper> schema = getSchema(ObjectWrapper.class);
            ObjectWrapper wrapper = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(data, wrapper, schema);
            return wrapper.getObject();
        } catch (Exception e) {
            throw new RpcException("Protobuf deserialization failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Schema<T> getSchema(Class<T> clazz) {
        return SCHEMA_CACHE.computeIfAbsent(clazz, RuntimeSchema::createFrom);
    }

    /**
     * Wrapper class to hold any object for Protostuff serialization.
     * Protostuff requires a concrete class with fields to serialize.
     */
    public static class ObjectWrapper {
        private Object object;

        public ObjectWrapper() {
        }

        public ObjectWrapper(Object object) {
            this.object = object;
        }

        public Object getObject() {
            return object;
        }

        public void setObject(Object object) {
            this.object = object;
        }
    }
}
