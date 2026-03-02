package com.xujn.nettyrpc.core.client;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.api.loadbalance.LoadBalancer;
import com.xujn.nettyrpc.common.model.RpcRequest;
import com.xujn.nettyrpc.common.model.RpcResponse;
import com.xujn.nettyrpc.api.registry.ServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.List;
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

    public RpcClientProxy(ServiceDiscovery serviceDiscovery, LoadBalancer loadBalancer,
                          NettyClient nettyClient, long timeoutMs) {
        this.serviceDiscovery = serviceDiscovery;
        this.loadBalancer = loadBalancer;
        this.nettyClient = nettyClient;
        this.timeoutMs = timeoutMs;
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

                    // 1. Build request
                    RpcRequest request = new RpcRequest(
                            REQUEST_ID_GEN.incrementAndGet(),
                            interfaceClass.getName(),
                            method.getName(),
                            method.getParameterTypes(),
                            args
                    );

                    // 2. Discover + load balance
                    String serviceName = interfaceClass.getName();
                    List<String> addresses = serviceDiscovery.discover(serviceName);
                    if (addresses == null || addresses.isEmpty()) {
                        throw new RpcException("No available servers for: " + serviceName);
                    }
                    String address = loadBalancer.select(addresses);

                    String[] parts = address.split(":");
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);

                    log.debug("Sending request {} to {}:{}", request.getRequestId(), host, port);

                    // 3. Send & await response with timeout
                    RpcResponse response = nettyClient.sendRequest(host, port, request)
                            .get(timeoutMs, TimeUnit.MILLISECONDS);

                    // 4. Handle response
                    if (!response.isSuccess()) {
                        throw new RpcException(response.getError());
                    }
                    return response.getResult();
                }
        );
    }
}
