package com.xujn.nettyrpc.example.springboot.consumer;

import com.xujn.nettyrpc.example.springboot.api.HelloService;
import com.xujn.nettyrpc.common.annotation.RpcReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @RpcReference(timeout = 5000, retries = 3)
    private HelloService helloService;

    @GetMapping("/hello")
    public String hello(@RequestParam(defaultValue = "Spring Boot") String name) {
        return helloService.sayHello(name);
    }
}
