package com.xujn.nettyrpc.core.loadbalance;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.api.loadbalance.LoadBalancer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random load balancing strategy.
 */
public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            throw new RpcException("No available service addresses");
        }
        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }
        return serviceAddresses.get(ThreadLocalRandom.current().nextInt(serviceAddresses.size()));
    }
}
