package com.xujn.nettyrpc.example.springboot.provider;

import com.xujn.nettyrpc.example.springboot.api.HelloService;
import com.xujn.nettyrpc.common.annotation.RpcService;
import org.springframework.stereotype.Service;

@Service
@RpcService(HelloService.class)
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String name) {
        return "Hello " + name + " from Spring Boot Provider!";
    }
}
