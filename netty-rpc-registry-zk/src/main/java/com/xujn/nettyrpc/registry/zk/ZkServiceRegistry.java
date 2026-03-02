package com.xujn.nettyrpc.registry.zk;

import com.xujn.nettyrpc.common.exception.RpcException;
import com.xujn.nettyrpc.api.registry.ServiceRegistry;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZooKeeper-based service registry implementation.
 * Uses persistent nodes for service paths and ephemeral nodes for instance addresses.
 */
public class ZkServiceRegistry implements ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ZkServiceRegistry.class);
    private static final String ROOT_PATH = "/netty-rpc/services";

    private final CuratorFramework zkClient;

    public ZkServiceRegistry(String zkAddress) {
        this.zkClient = CuratorFrameworkFactory.builder()
                .connectString(zkAddress)
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(3000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        this.zkClient.start();
        log.info("ZkServiceRegistry connected to {}", zkAddress);
    }

    /**
     * Constructor accepting an externally managed CuratorFramework (for testing).
     */
    public ZkServiceRegistry(CuratorFramework zkClient) {
        this.zkClient = zkClient;
    }

    @Override
    public void register(String serviceName, String serviceAddress) {
        try {
            String servicePath = ROOT_PATH + "/" + serviceName;
            if (zkClient.checkExists().forPath(servicePath) == null) {
                zkClient.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .forPath(servicePath);
            }
            String addressPath = servicePath + "/" + serviceAddress;
            if (zkClient.checkExists().forPath(addressPath) != null) {
                zkClient.delete().forPath(addressPath);
            }
            zkClient.create()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(addressPath);
            log.info("Registered service: {} at {}", serviceName, serviceAddress);
        } catch (Exception e) {
            throw new RpcException("Failed to register service: " + serviceName, e);
        }
    }

    @Override
    public void unregister(String serviceName, String serviceAddress) {
        try {
            String addressPath = ROOT_PATH + "/" + serviceName + "/" + serviceAddress;
            if (zkClient.checkExists().forPath(addressPath) != null) {
                zkClient.delete().forPath(addressPath);
                log.info("Unregistered service: {} at {}", serviceName, serviceAddress);
            }
        } catch (Exception e) {
            throw new RpcException("Failed to unregister service: " + serviceName, e);
        }
    }
}
