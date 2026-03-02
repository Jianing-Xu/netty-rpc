package com.xujn.nettyrpc.domain.registry;

/**
 * Domain interface for publishing service endpoints to a registry.
 */
public interface ServiceRegistry {

    /**
     * Register a service implementation at the given network address.
     *
     * @param serviceName    fully-qualified interface name
     * @param serviceAddress host:port string
     */
    void register(String serviceName, String serviceAddress);

    /**
     * Remove a previously registered service endpoint.
     */
    void unregister(String serviceName, String serviceAddress);
}
