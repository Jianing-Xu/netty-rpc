package com.xujn.nettyrpc.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an RPC service implementation.
 * The annotated class must implement the specified interface.
 *
 * Usage:
 * <pre>
 * {@literal @}RpcService(HelloService.class)
 * public class HelloServiceImpl implements HelloService { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService {
    /**
     * The service interface to expose.
     */
    Class<?> value();
}
