package com.xujn.nettyrpc.core.server;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.common.model.RpcMessage;
import com.xujn.nettyrpc.common.model.RpcRequest;
import com.xujn.nettyrpc.common.model.RpcResponse;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-side Netty handler that dispatches RPC requests to local service beans
 * via reflection, using a dedicated business thread pool to avoid blocking NIO threads.
 */
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(RpcServerHandler.class);

    private final Map<String, Object> serviceMap;
    private final ExecutorService bizThreadPool;

    public RpcServerHandler(Map<String, Object> serviceMap) {
        this(serviceMap, Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2));
    }

    public RpcServerHandler(Map<String, Object> serviceMap, ExecutorService bizThreadPool) {
        this.serviceMap = serviceMap;
        this.bizThreadPool = bizThreadPool;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        RpcRequest request = (RpcRequest) msg.getBody();
        bizThreadPool.submit(() -> {
            RpcResponse response;
            try {
                Object result = invokeService(request);
                response = RpcResponse.success(request.getRequestId(), result);
            } catch (Throwable t) {
                log.error("Service invocation failed for {}.{}",
                        request.getClassName(), request.getMethodName(), t);
                response = RpcResponse.error(request.getRequestId(), t);
            }
            RpcMessage respMsg = RpcMessage.buildResponse(response);
            ctx.writeAndFlush(respMsg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        });
    }

    /**
     * Reflectively invoke the target service method.
     */
    Object invokeService(RpcRequest request) throws Exception {
        String className = request.getClassName();
        Object serviceBean = serviceMap.get(className);
        if (serviceBean == null) {
            throw new RpcException("Service not found: " + className);
        }

        Method method = serviceBean.getClass().getMethod(
                request.getMethodName(), request.getParameterTypes());
        return method.invoke(serviceBean, request.getParameters());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Server handler exception", cause);
        ctx.close();
    }
}
