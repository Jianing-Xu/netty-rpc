package com.xujn.nettyrpc.spring.boot.autoconfigure;

import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

public class RpcServerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RpcServerRunner.class);
    private final RpcBootstrap rpcBootstrap;
    private final boolean serverEnable;

    public RpcServerRunner(RpcBootstrap rpcBootstrap, boolean serverEnable) {
        this.rpcBootstrap = rpcBootstrap;
        this.serverEnable = serverEnable;
    }

    @Override
    public void run(String... args) throws Exception {
        if (serverEnable) {
            log.info("Starting Netty RPC Server via Spring Boot...");
            rpcBootstrap.startServer();
        } else {
            log.info("Netty RPC Server is disabled (netty-rpc.server-enable=false). Running in client-only mode.");
        }
    }
}
