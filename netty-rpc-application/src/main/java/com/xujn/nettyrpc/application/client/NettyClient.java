package com.xujn.nettyrpc.application.client;

import com.xujn.nettyrpc.domain.exception.RpcException;
import com.xujn.nettyrpc.domain.model.RpcMessage;
import com.xujn.nettyrpc.domain.model.RpcRequest;
import com.xujn.nettyrpc.domain.model.RpcResponse;
import com.xujn.nettyrpc.domain.protocol.Serializer;
import com.xujn.nettyrpc.infrastructure.codec.RpcDecoder;
import com.xujn.nettyrpc.infrastructure.codec.RpcEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty-based RPC client that manages connections and sends requests.
 * Maintains a channel cache for connection reuse.
 */
public class NettyClient {

    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final PendingRequests pendingRequests;
    private final ConcurrentHashMap<String, Channel> channelCache = new ConcurrentHashMap<>();

    public NettyClient(Serializer serializer) {
        this.pendingRequests = new PendingRequests();
        this.eventLoopGroup = new NioEventLoopGroup(4);
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new RpcDecoder(serializer))
                                .addLast(new RpcEncoder(serializer))
                                .addLast(new RpcClientHandler(pendingRequests));
                    }
                });
    }

    /**
     * Send an RPC request and return a CompletableFuture for the response.
     */
    public CompletableFuture<RpcResponse> sendRequest(String host, int port, RpcRequest request) {
        CompletableFuture<RpcResponse> future = pendingRequests.put(request.getRequestId());

        try {
            Channel channel = getOrCreateChannel(host, port);
            RpcMessage msg = RpcMessage.buildRequest(request);

            channel.writeAndFlush(msg).addListener((ChannelFutureListener) channelFuture -> {
                if (!channelFuture.isSuccess()) {
                    future.completeExceptionally(channelFuture.cause());
                    pendingRequests.remove(request.getRequestId());
                    log.error("Failed to send request {}", request.getRequestId(), channelFuture.cause());
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
            pendingRequests.remove(request.getRequestId());
        }

        return future;
    }

    private Channel getOrCreateChannel(String host, int port) {
        String key = host + ":" + port;
        Channel channel = channelCache.get(key);
        if (channel != null && channel.isActive()) {
            return channel;
        }

        try {
            Channel newChannel = bootstrap.connect(host, port).sync().channel();
            channelCache.put(key, newChannel);
            newChannel.closeFuture().addListener(future -> channelCache.remove(key));
            log.info("Connected to {}:{}", host, port);
            return newChannel;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcException("Connection interrupted to " + key, e);
        }
    }

    public void shutdown() {
        channelCache.values().forEach(Channel::close);
        channelCache.clear();
        eventLoopGroup.shutdownGracefully();
    }
}
