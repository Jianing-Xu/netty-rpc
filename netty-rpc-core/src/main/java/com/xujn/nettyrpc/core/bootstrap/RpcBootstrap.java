package com.xujn.nettyrpc.core.bootstrap;

import com.xujn.nettyrpc.core.client.NettyClient;
import com.xujn.nettyrpc.core.client.RpcClientProxy;
import com.xujn.nettyrpc.core.server.RpcServer;
import com.xujn.nettyrpc.common.annotation.RpcReference;
import com.xujn.nettyrpc.common.annotation.RpcService;
import com.xujn.nettyrpc.api.loadbalance.LoadBalancer;
import com.xujn.nettyrpc.api.serialization.Serializer;
import com.xujn.nettyrpc.api.registry.ServiceDiscovery;
import com.xujn.nettyrpc.api.registry.ServiceRegistry;
import com.xujn.nettyrpc.core.loadbalance.RoundRobinLoadBalancer;
import com.xujn.nettyrpc.core.serialization.ProtobufSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

/**
 * Bootstrap class for annotation-driven RPC framework usage.
 *
 * <p>Server-side usage:</p>
 * <pre>
 * RpcBootstrap bootstrap = RpcBootstrap.builder()
 *     .serializer(new ProtobufSerializer())
 *     .registry(new ZkServiceRegistry("127.0.0.1:2181"))
 *     .host("127.0.0.1").port(8080)
 *     .build();
 *
 * bootstrap.scanServices("com.example.provider"); // scans @RpcService
 * bootstrap.startServer();
 * </pre>
 *
 * <p>Client-side usage:</p>
 * <pre>
 * RpcBootstrap bootstrap = RpcBootstrap.builder()
 *     .serializer(new ProtobufSerializer())
 *     .discovery(new ZkServiceDiscovery("127.0.0.1:2181"))
 *     .build();
 *
 * MyConsumer consumer = new MyConsumer();
 * bootstrap.injectReferences(consumer); // injects @RpcReference fields
 * </pre>
 */
public class RpcBootstrap {

    private static final Logger log = LoggerFactory.getLogger(RpcBootstrap.class);

    private final Serializer serializer;
    private final ServiceRegistry registry;
    private final ServiceDiscovery discovery;
    private final LoadBalancer loadBalancer;
    private final String host;
    private final int port;

    private RpcServer rpcServer;
    private NettyClient nettyClient;
    private final Map<String, Object> registeredServices = new HashMap<>();

    private RpcBootstrap(Builder builder) {
        this.serializer = builder.serializer;
        this.registry = builder.registry;
        this.discovery = builder.discovery;
        this.loadBalancer = builder.loadBalancer;
        this.host = builder.host;
        this.port = builder.port;
    }

    /**
     * Scan a package for classes annotated with @RpcService and register them.
     */
    public void scanServices(String basePackage) {
        List<Class<?>> classes = scanClasses(basePackage);
        for (Class<?> clazz : classes) {
            RpcService annotation = clazz.getAnnotation(RpcService.class);
            if (annotation != null) {
                Class<?> serviceInterface = annotation.value();
                try {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    registeredServices.put(serviceInterface.getName(), instance);
                    log.info("Scanned @RpcService: {} -> {}", serviceInterface.getName(), clazz.getName());
                } catch (Exception e) {
                    log.error("Failed to instantiate @RpcService: {}", clazz.getName(), e);
                }
            }
        }
    }

    /**
     * Register a service bean directly (without scanning).
     */
    public void addService(Class<?> serviceInterface, Object serviceBean) {
        registeredServices.put(serviceInterface.getName(), serviceBean);
    }

    /**
     * Start the RPC server with all registered services.
     */
    public void startServer() throws InterruptedException {
        if (registry == null) {
            throw new IllegalStateException("ServiceRegistry must be set for server mode");
        }
        rpcServer = new RpcServer(host, port, registry, serializer);
        for (Map.Entry<String, Object> entry : registeredServices.entrySet()) {
            rpcServer.addService(entry.getKey(), entry.getValue());
        }
        rpcServer.start();
    }

    /**
     * Inject RPC proxy instances into fields annotated with @RpcReference.
     */
    public void injectReferences(Object target) {
        if (discovery == null) {
            throw new IllegalStateException("ServiceDiscovery must be set for client mode");
        }
        if (nettyClient == null) {
            nettyClient = new NettyClient(serializer);
        }

        Class<?> clazz = target.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            RpcReference ref = field.getAnnotation(RpcReference.class);
            if (ref != null) {
                Class<?> fieldType = field.getType();
                RpcClientProxy proxy = new RpcClientProxy(
                        discovery, loadBalancer, nettyClient, ref.timeout(), ref.retries());
                Object proxyInstance = proxy.create(fieldType);

                field.setAccessible(true);
                try {
                    field.set(target, proxyInstance);
                    log.info("Injected @RpcReference: {} (timeout={}ms)",
                            fieldType.getName(), ref.timeout());
                } catch (IllegalAccessException e) {
                    log.error("Failed to inject @RpcReference: {}", field.getName(), e);
                }
            }
        }
    }

    public void shutdown() {
        if (rpcServer != null) rpcServer.shutdown();
        if (nettyClient != null) nettyClient.shutdown();
    }

    /**
     * Scan all classes in a package using classpath traversal.
     */
    private List<Class<?>> scanClasses(String basePackage) {
        List<Class<?>> classes = new ArrayList<>();
        String path = basePackage.replace('.', '/');
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File dir = new File(resource.toURI());
                if (dir.isDirectory()) {
                    scanDirectory(dir, basePackage, classes);
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan package: {}", basePackage, e);
        }
        return classes;
    }

    private void scanDirectory(File dir, String packageName, List<Class<?>> classes) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." +
                        file.getName().substring(0, file.getName().length() - 6);
                try {
                    classes.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    log.debug("Class not found: {}", className);
                }
            }
        }
    }

    // ── Builder ──

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Serializer serializer = new ProtobufSerializer();
        private ServiceRegistry registry;
        private ServiceDiscovery discovery;
        private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        private String host = "127.0.0.1";
        private int port = 8080;

        public Builder serializer(Serializer serializer) {
            this.serializer = serializer;
            return this;
        }
        public Builder registry(ServiceRegistry registry) {
            this.registry = registry;
            return this;
        }
        public Builder discovery(ServiceDiscovery discovery) {
            this.discovery = discovery;
            return this;
        }
        public Builder loadBalancer(LoadBalancer loadBalancer) {
            this.loadBalancer = loadBalancer;
            return this;
        }
        public Builder host(String host) {
            this.host = host;
            return this;
        }
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        public RpcBootstrap build() {
            return new RpcBootstrap(this);
        }
    }
}
