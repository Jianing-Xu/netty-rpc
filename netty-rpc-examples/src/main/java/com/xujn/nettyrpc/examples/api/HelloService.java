package com.xujn.nettyrpc.examples.api;

/**
 * Service API shared between provider and consumer.
 */
public interface HelloService {
    String sayHello(String name);
    int add(int a, int b);
}
