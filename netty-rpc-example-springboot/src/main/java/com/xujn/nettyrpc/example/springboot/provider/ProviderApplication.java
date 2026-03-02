package com.xujn.nettyrpc.example.springboot.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProviderApplication {
    public static void main(String[] args) {
        // Run with: -Dspring.config.name=application-provider
        System.setProperty("spring.config.name", "application-provider");
        SpringApplication.run(ProviderApplication.class, args);
    }
}
