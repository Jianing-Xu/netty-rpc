package com.xujn.nettyrpc.spring.boot.autoconfigure;

import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.common.annotation.RpcService;
import com.xujn.nettyrpc.common.annotation.RpcReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class RpcBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RpcBeanPostProcessor.class);
    private final RpcBootstrap rpcBootstrap;

    public RpcBeanPostProcessor(RpcBootstrap rpcBootstrap) {
        this.rpcBootstrap = rpcBootstrap;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Register @RpcService exported beans
        Class<?> clazz = bean.getClass();
        RpcService rpcService = clazz.getAnnotation(RpcService.class);
        if (rpcService != null) {
            Class<?> serviceInterface = rpcService.value();
            int limit = rpcService.limit();
            log.info("Spring Boot Starter scanning found @RpcService: {} -> {} (limit: {})", 
                     serviceInterface.getName(), clazz.getName(), limit);
            rpcBootstrap.addService(serviceInterface, bean, limit);
        }

        // Inject @RpcReference references into the bean fields
        try {
            rpcBootstrap.injectReferences(bean);
        } catch (Exception e) {
            log.error("Failed to inject @RpcReference for bean {}", beanName, e);
        }

        return bean;
    }
}
