package com.xujn.nettyrpc.examples.provider;

import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.registry.zk.ZkServiceRegistry;
import com.xujn.nettyrpc.core.serialization.ProtobufSerializer;

public class ProviderApp {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting RPC Provider...");

        // Start Bootstrapper using annotations, connecting to local ZK
        RpcBootstrap bootstrap = RpcBootstrap.builder()
                .registry(new ZkServiceRegistry("127.0.0.1:2181"))
                .serializer(new ProtobufSerializer())
                .host("127.0.0.1")
                .port(8080)
                .build();

        // Scan current package for @RpcService annotations
        bootstrap.scanServices("com.xujn.nettyrpc.examples.provider");
        
        System.out.println("Services scanned. Binding to port 8080...");
        bootstrap.startServer();
        System.out.println("Provider started successfully!");

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::shutdown));
    }
}
