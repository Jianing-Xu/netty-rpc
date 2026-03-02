package com.xujn.nettyrpc.spring.boot.autoconfigure;

import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.api.registry.ServiceDiscovery;
import com.xujn.nettyrpc.api.registry.ServiceRegistry;
import com.xujn.nettyrpc.registry.zk.ZkServiceDiscovery;
import com.xujn.nettyrpc.registry.zk.ZkServiceRegistry;
import com.xujn.nettyrpc.core.serialization.ProtobufSerializer;
import com.xujn.nettyrpc.core.loadbalance.RoundRobinLoadBalancer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * Spring Boot AutoConfiguration for Netty RPC.
 */
@Configuration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcAutoConfiguration {

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    public CuratorFramework zkClient(RpcProperties properties) {
        return CuratorFrameworkFactory.builder()
                .connectString(properties.getZkAddress())
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(3000)
                .retryPolicy(new ExponentialBackoffRetry(500, 3))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceRegistry serviceRegistry(CuratorFramework zkClient) {
        return new ZkServiceRegistry(zkClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceDiscovery serviceDiscovery(CuratorFramework zkClient) {
        return new ZkServiceDiscovery(zkClient);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RpcBootstrap rpcBootstrap(RpcProperties properties, 
                                     ServiceRegistry registry, 
                                     ServiceDiscovery discovery) {
        return RpcBootstrap.builder()
                .registry(registry)
                .discovery(discovery)
                .host(properties.getServerAddress())
                .port(properties.getServerPort())
                .serializer(new ProtobufSerializer())
                .loadBalancer(new RoundRobinLoadBalancer())
                .build();
    }

    @Bean
    public RpcBeanPostProcessor rpcBeanPostProcessor(RpcBootstrap rpcBootstrap) {
        return new RpcBeanPostProcessor(rpcBootstrap);
    }

    // Bean that runs our server on startup if enabled
    @Bean
    public RpcServerRunner rpcServerRunner(RpcBootstrap rpcBootstrap, RpcProperties properties) {
        return new RpcServerRunner(rpcBootstrap, properties.isServerEnable());
    }
}
