package com.xujn.nettyrpc.examples.consumer;

import com.xujn.nettyrpc.core.bootstrap.RpcBootstrap;
import com.xujn.nettyrpc.examples.api.HelloService;
import com.xujn.nettyrpc.common.annotation.RpcReference;
import com.xujn.nettyrpc.core.loadbalance.RoundRobinLoadBalancer;
import com.xujn.nettyrpc.registry.zk.ZkServiceDiscovery;
import com.xujn.nettyrpc.core.serialization.ProtobufSerializer;

public class ConsumerApp {

    // Automatically inject the proxy for HelloService using ZK discovery
    @RpcReference(timeout = 5000)
    private HelloService helloService;

    public void run() {
        System.out.println("\n--- Invoking sayHello ---");
        String result = helloService.sayHello("Developer");
        System.out.println("<- [Consumer] Result: " + result);

        System.out.println("\n--- Invoking add ---");
        int sum = helloService.add(100, 200);
        System.out.println("<- [Consumer] Sum: " + sum);
        System.out.println("-------------------------");
    }

    public static void main(String[] args) {
        System.out.println("Starting RPC Consumer...");

        // Initialize bootstrap for client side
        RpcBootstrap bootstrap = RpcBootstrap.builder()
                .discovery(new ZkServiceDiscovery("127.0.0.1:2181"))
                .loadBalancer(new RoundRobinLoadBalancer())
                .serializer(new ProtobufSerializer())
                .build();

        ConsumerApp app = new ConsumerApp();
        bootstrap.injectReferences(app);

        // Run the actual calls
        try {
            app.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bootstrap.shutdown();
        }
    }
}
