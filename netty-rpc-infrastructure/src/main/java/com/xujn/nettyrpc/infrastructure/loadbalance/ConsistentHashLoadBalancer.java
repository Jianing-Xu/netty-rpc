package com.xujn.nettyrpc.infrastructure.loadbalance;

import com.xujn.nettyrpc.domain.exception.RpcException;
import com.xujn.nettyrpc.domain.loadbalance.LoadBalancer;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consistent hashing load balancer.
 * Maps service addresses onto a virtual hash ring with replicas.
 * Ensures minimal redistribution when nodes are added/removed.
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {

    private static final int VIRTUAL_NODES = 160;
    private final ConcurrentHashMap<String, TreeMap<Long, String>> ringCache =
            new ConcurrentHashMap<>();

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            throw new RpcException("No available service addresses");
        }
        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }

        // Build ring key from sorted address list
        String key = String.join(",", serviceAddresses.stream().sorted().toList());
        TreeMap<Long, String> ring = ringCache.computeIfAbsent(key,
                k -> buildRing(serviceAddresses));

        // Hash the current thread + timestamp as routing key (per-request distribution)
        long hash = hash(Thread.currentThread().getName() + System.nanoTime());
        return locate(ring, hash);
    }

    /**
     * Select with an explicit routing key (e.g., user ID, order ID).
     */
    public String selectWithKey(List<String> serviceAddresses, String routingKey) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            throw new RpcException("No available service addresses");
        }
        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }

        String key = String.join(",", serviceAddresses.stream().sorted().toList());
        TreeMap<Long, String> ring = ringCache.computeIfAbsent(key,
                k -> buildRing(serviceAddresses));

        long hash = hash(routingKey);
        return locate(ring, hash);
    }

    private TreeMap<Long, String> buildRing(List<String> addresses) {
        TreeMap<Long, String> ring = new TreeMap<>();
        for (String addr : addresses) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                long h = hash(addr + "#" + i);
                ring.put(h, addr);
            }
        }
        return ring;
    }

    private String locate(TreeMap<Long, String> ring, long hash) {
        SortedMap<Long, String> tailMap = ring.tailMap(hash);
        long target = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(target);
    }

    /**
     * FNV1a_32 hash for good distribution.
     */
    static long hash(String key) {
        final int FNV_32_PRIME = 0x01000193;
        int hash = 0x811c9dc5;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= FNV_32_PRIME;
        }
        return hash & 0xFFFFFFFFL;
    }
}
