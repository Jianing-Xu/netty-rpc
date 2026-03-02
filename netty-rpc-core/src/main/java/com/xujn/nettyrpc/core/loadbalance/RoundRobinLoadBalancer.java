package com.xujn.nettyrpc.core.loadbalance;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.api.loadbalance.LoadBalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancing strategy.
 * Uses an atomic counter to cycle through available addresses.
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            throw new RpcException("No available service addresses");
        }
        int size = serviceAddresses.size();
        int index = (counter.getAndIncrement() & Integer.MAX_VALUE) % size;
        return serviceAddresses.get(index);
    }
}
