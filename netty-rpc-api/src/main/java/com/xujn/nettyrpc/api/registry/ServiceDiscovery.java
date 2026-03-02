package com.xujn.nettyrpc.api.registry;

import java.util.List;

/**
 * Domain interface for discovering available service endpoints.
 */
public interface ServiceDiscovery {

    /**
     * Discover all available addresses for the given service.
     *
     * @param serviceName fully-qualified interface name
     * @return list of host:port strings
     */
    List<String> discover(String serviceName);
}
