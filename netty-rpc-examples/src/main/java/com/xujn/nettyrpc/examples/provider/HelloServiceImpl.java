package com.xujn.nettyrpc.examples.provider;

import com.xujn.nettyrpc.examples.api.HelloService;
import com.xujn.nettyrpc.common.annotation.RpcService;

/**
 * Service Implementation using the @RpcService annotation.
 */
@RpcService(HelloService.class)
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String name) {
        System.out.println("-> [Provider] Received sayHello request for: " + name);
        return "Hello from netty-rpc, " + name + "!";
    }

    @Override
    public int add(int a, int b) {
        System.out.println("-> [Provider] Received add request: " + a + " + " + b);
        return a + b;
    }
}
