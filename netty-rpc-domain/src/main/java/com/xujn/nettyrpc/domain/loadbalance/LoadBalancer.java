package com.xujn.nettyrpc.domain.loadbalance;

import java.util.List;

/**
 * Domain interface for selecting a service endpoint from available candidates.
 */
public interface LoadBalancer {

    /**
     * Select one address from the list of available service addresses.
     *
     * @param serviceAddresses non-empty list of host:port strings
     * @return the selected host:port
     */
    String select(List<String> serviceAddresses);
}
