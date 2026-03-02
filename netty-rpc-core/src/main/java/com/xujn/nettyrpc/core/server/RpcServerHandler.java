package com.xujn.nettyrpc.core.server;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.api.ratelimit.RateLimiter;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-side Netty handler that dispatches RPC requests to local service beans
 * via reflection, using a dedicated business thread pool to avoid blocking NIO threads.
 */
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(RpcServerHandler.class);

    private final Map<String, Object> serviceMap;
    private final Map<String, RateLimiter> rateLimiterMap;
    private final ExecutorService bizThreadPool;

    public RpcServerHandler(Map<String, Object> serviceMap) {
        this(serviceMap, null, Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2));
    }

    public RpcServerHandler(Map<String, Object> serviceMap, Map<String, RateLimiter> rateLimiterMap) {
        this(serviceMap, rateLimiterMap, Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2));
    }

    public RpcServerHandler(Map<String, Object> serviceMap, Map<String, RateLimiter> rateLimiterMap, ExecutorService bizThreadPool) {
        this.serviceMap = serviceMap;
        this.rateLimiterMap = rateLimiterMap;
        this.bizThreadPool = bizThreadPool;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        RpcRequest request = (RpcRequest) msg.getBody();
        bizThreadPool.submit(() -> {
            RpcResponse response;
            try {
                String className = request.getClassName();
                if (rateLimiterMap != null) {
                    RateLimiter rateLimiter = rateLimiterMap.get(className);
                    if (rateLimiter != null && !rateLimiter.tryAcquire()) {
                        throw new RpcException("Server is busy. Rate limit exceeded for service: " + className);
                    }
                }

                Object result = invokeService(request);
                if (result instanceof CompletableFuture) {
                    // Async method response
                    ((CompletableFuture<?>) result).whenComplete((res, ex) -> {
                        RpcResponse asyncResponse;
                        if (ex != null) {
                            log.error("Async service invocation failed for {}.{}",
                                    request.getClassName(), request.getMethodName(), ex);
                            asyncResponse = RpcResponse.error(request.getRequestId(), ex);
                        } else {
                            asyncResponse = RpcResponse.success(request.getRequestId(), res);
                        }
                        RpcMessage asyncRespMsg = RpcMessage.buildResponse(asyncResponse);
                        ctx.writeAndFlush(asyncRespMsg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                    });
                    // Do not execute writeAndFlush below.
                    return;
                }
                
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
