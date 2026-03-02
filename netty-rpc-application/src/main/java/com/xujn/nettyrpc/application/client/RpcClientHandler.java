package com.xujn.nettyrpc.application.client;

import com.xujn.nettyrpc.domain.model.RpcMessage;
import com.xujn.nettyrpc.domain.model.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side Netty handler that receives RPC responses from the server
 * and completes the corresponding pending future.
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(RpcClientHandler.class);
    private final PendingRequests pendingRequests;

    public RpcClientHandler(PendingRequests pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        RpcResponse response = (RpcResponse) msg.getBody();
        log.debug("Received response for requestId={}", response.getRequestId());
        pendingRequests.complete(response.getRequestId(), response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Client handler exception", cause);
        ctx.close();
    }
}
