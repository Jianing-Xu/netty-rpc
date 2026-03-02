package com.xujn.nettyrpc.registry.zk;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.api.registry.ServiceDiscovery;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * ZooKeeper-based service discovery implementation.
 * Fetches child nodes (provider addresses) under the service path.
 */
public class ZkServiceDiscovery implements ServiceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ZkServiceDiscovery.class);
    private static final String ROOT_PATH = "/netty-rpc/services";

    private final CuratorFramework zkClient;

    public ZkServiceDiscovery(String zkAddress) {
        this.zkClient = CuratorFrameworkFactory.builder()
                .connectString(zkAddress)
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(3000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        this.zkClient.start();
        log.info("ZkServiceDiscovery connected to {}", zkAddress);
    }

    /**
     * Constructor accepting an externally managed CuratorFramework (for testing).
     */
    public ZkServiceDiscovery(CuratorFramework zkClient) {
        this.zkClient = zkClient;
    }

    @Override
    public List<String> discover(String serviceName) {
        try {
            String servicePath = ROOT_PATH + "/" + serviceName;
            if (zkClient.checkExists().forPath(servicePath) == null) {
                log.warn("Service path not found: {}", servicePath);
                return Collections.emptyList();
            }
            List<String> addresses = zkClient.getChildren().forPath(servicePath);
            log.debug("Discovered {} addresses for service {}", addresses.size(), serviceName);
            return addresses;
        } catch (Exception e) {
            throw new RpcException("Failed to discover service: " + serviceName, e);
        }
    }
}
