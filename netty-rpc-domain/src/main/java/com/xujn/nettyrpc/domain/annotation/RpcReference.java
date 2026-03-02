package com.xujn.nettyrpc.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for automatic RPC proxy injection.
 *
 * Usage:
 * <pre>
 * public class OrderHandler {
 *     {@literal @}RpcReference(timeout = 3000)
 *     private HelloService helloService;
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcReference {
    /**
     * RPC call timeout in milliseconds.
     */
    long timeout() default 5000;
}
