package com.xujn.nettyrpc.core.client;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.api.loadbalance.LoadBalancer;
import com.xujn.nettyrpc.common.model.RpcRequest;
import com.xujn.nettyrpc.common.model.RpcResponse;
import com.xujn.nettyrpc.api.registry.ServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates JDK dynamic proxies for RPC service interfaces.
 * Intercepts method calls and translates them into network RPC requests.
 */
public class RpcClientProxy {

    private static final Logger log = LoggerFactory.getLogger(RpcClientProxy.class);
    private static final AtomicLong REQUEST_ID_GEN = new AtomicLong(0);

    private final ServiceDiscovery serviceDiscovery;
    private final LoadBalancer loadBalancer;
    private final NettyClient nettyClient;
    private final long timeoutMs;
    private final int retries;

    public RpcClientProxy(ServiceDiscovery serviceDiscovery, LoadBalancer loadBalancer,
                          NettyClient nettyClient, long timeoutMs, int retries) {
        this.serviceDiscovery = serviceDiscovery;
        this.loadBalancer = loadBalancer;
        this.nettyClient = nettyClient;
        this.timeoutMs = timeoutMs;
        this.retries = retries;
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                (proxy, method, args) -> {
                    // Skip Object methods
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }

                    String serviceName = interfaceClass.getName();
                    int maxTries = retries + 1;
                    
                    boolean isAsync = CompletableFuture.class.isAssignableFrom(method.getReturnType());
                    if (isAsync) {
                        return sendAsyncWithRetry(serviceName, method, args, 1, maxTries);
                    }

                    int tryCount = 0;
                    Throwable lastException = null;

                    while (tryCount < maxTries) {
                        tryCount++;
                        try {
                            // 1. Build request
                            RpcRequest request = new RpcRequest(
                                    REQUEST_ID_GEN.incrementAndGet(),
                                    serviceName,
                                    method.getName(),
                                    method.getParameterTypes(),
                                    args
                            );

                            // 2. Discover + load balance
                            List<String> addresses = serviceDiscovery.discover(serviceName);
                            if (addresses == null || addresses.isEmpty()) {
                                throw new RpcException("No available servers for: " + serviceName);
                            }
                            String address = loadBalancer.select(addresses);

                            String[] parts = address.split(":");
                            String host = parts[0];
                            int port = Integer.parseInt(parts[1]);

                            log.debug("Sending request {} to {}:{} (try {}/{})", 
                                      request.getRequestId(), host, port, tryCount, maxTries);

                            // 3. Send & await response with timeout
                            RpcResponse response = nettyClient.sendRequest(host, port, request)
                                    .get(timeoutMs, TimeUnit.MILLISECONDS);

                            // 4. Handle response
                            if (!response.isSuccess()) {
                                throw new RpcException(response.getError());
                            }
                            return response.getResult();

                        } catch (Exception e) {
                            lastException = e;
                            if (tryCount < maxTries) {
                                log.warn("RPC invocation failed, retrying... (try {}/{}): {}", 
                                         tryCount, maxTries, e.getMessage());
                            }
                        }
                    }

                    log.error("RPC invocation failed after {} tries for service {}", maxTries, serviceName, lastException);
                    throw new RpcException("RPC invocation failed after " + maxTries + " tries: " + lastException.getMessage(), lastException);
                }
        );
    }

    private CompletableFuture<Object> sendAsyncWithRetry(String serviceName, Method method, Object[] args, int tryCount, int maxTries) {
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        
        try {
            RpcRequest request = new RpcRequest(
                    REQUEST_ID_GEN.incrementAndGet(),
                    serviceName,
                    method.getName(),
                    method.getParameterTypes(),
                    args
            );

            List<String> addresses = serviceDiscovery.discover(serviceName);
            if (addresses == null || addresses.isEmpty()) {
                throw new RpcException("No available servers for: " + serviceName);
            }
            String address = loadBalancer.select(addresses);
            String[] parts = address.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            log.debug("Sending async request {} to {}:{} (try {}/{})", 
                      request.getRequestId(), host, port, tryCount, maxTries);

            nettyClient.sendRequest(host, port, request)
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .whenComplete((response, ex) -> {
                        if (ex != null) {
                            handleAsyncFailure(serviceName, method, args, tryCount, maxTries, ex, resultFuture);
                        } else if (!response.isSuccess()) {
                            handleAsyncFailure(serviceName, method, args, tryCount, maxTries, new RpcException(response.getError()), resultFuture);
                        } else {
                            resultFuture.complete(response.getResult());
                        }
                    });
        } catch (Exception e) {
            handleAsyncFailure(serviceName, method, args, tryCount, maxTries, e, resultFuture);
        }

        return resultFuture;
    }

    private void handleAsyncFailure(String serviceName, Method method, Object[] args, 
                                    int tryCount, int maxTries, Throwable ex, CompletableFuture<Object> resultFuture) {
        if (tryCount < maxTries) {
            log.warn("Async RPC invocation failed, retrying... (try {}/{}): {}", 
                     tryCount, maxTries, ex.getMessage());
            sendAsyncWithRetry(serviceName, method, args, tryCount + 1, maxTries)
                    .whenComplete((res, nextEx) -> {
                        if (nextEx != null) {
                            resultFuture.completeExceptionally(nextEx);
                        } else {
                            resultFuture.complete(res);
                        }
                    });
        } else {
            log.error("Async RPC invocation failed after {} tries for service {}", maxTries, serviceName, ex);
            resultFuture.completeExceptionally(new RpcException("RPC invocation failed after " + maxTries + " tries: " + ex.getMessage(), ex));
        }
    }
}
