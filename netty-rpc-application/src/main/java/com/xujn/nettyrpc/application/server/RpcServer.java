package com.xujn.nettyrpc.application.server;

import com.xujn.nettyrpc.domain.protocol.Serializer;
import com.xujn.nettyrpc.domain.registry.ServiceRegistry;
import com.xujn.nettyrpc.infrastructure.codec.RpcDecoder;
import com.xujn.nettyrpc.infrastructure.codec.RpcEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * RPC server that:
 * 1. Registers local service beans
 * 2. Publishes them to the service registry
 * 3. Starts a Netty server to handle incoming RPC requests
 */
public class RpcServer {

    private static final Logger log = LoggerFactory.getLogger(RpcServer.class);

    private final String host;
    private final int port;
    private final ServiceRegistry serviceRegistry;
    private final Serializer serializer;
    private final Map<String, Object> serviceMap = new HashMap<>();
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public RpcServer(String host, int port, ServiceRegistry serviceRegistry, Serializer serializer) {
        this.host = host;
        this.port = port;
        this.serviceRegistry = serviceRegistry;
        this.serializer = serializer;
    }

    /**
     * Register a local service bean for the given interface.
     */
    public void addService(String interfaceName, Object serviceBean) {
        serviceMap.put(interfaceName, serviceBean);
        log.info("Service registered locally: {}", interfaceName);
    }

    /**
     * Start the Netty server and publish services to the registry.
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new RpcDecoder(serializer))
                                .addLast(new RpcEncoder(serializer))
                                .addLast(new RpcServerHandler(serviceMap));
                    }
                });

        ChannelFuture future = bootstrap.bind(host, port).sync();
        serverChannel = future.channel();

        // Publish all registered services to the registry
        String address = host + ":" + port;
        for (String interfaceName : serviceMap.keySet()) {
            serviceRegistry.register(interfaceName, address);
        }

        log.info("RPC Server started on {}:{}", host, port);
    }

    /**
     * Gracefully shut down the server.
     */
    public void shutdown() {
        // Unregister services
        String address = host + ":" + port;
        for (String interfaceName : serviceMap.keySet()) {
            try {
                serviceRegistry.unregister(interfaceName, address);
            } catch (Exception e) {
                log.warn("Failed to unregister service: {}", interfaceName, e);
            }
        }

        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("RPC Server shut down");
    }
}
