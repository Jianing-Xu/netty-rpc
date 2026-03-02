package com.xujn.nettyrpc.core.loadbalance;

import com.xujn.nettyrpc.common.exception.RpcException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancerTest {

    // ── RandomLoadBalancer ──

    @Test
    void randomSelectsSingleAddress() {
        var lb = new RandomLoadBalancer();
        assertEquals("a", lb.select(List.of("a")));
    }

    @Test
    void randomSelectsFromMultiple() {
        var lb = new RandomLoadBalancer();
        List<String> addrs = List.of("a", "b", "c");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(lb.select(addrs));
        }
        // With 100 iterations and 3 options, all should appear
        assertEquals(3, seen.size());
    }

    @Test
    void randomThrowsOnEmpty() {
        var lb = new RandomLoadBalancer();
        assertThrows(RpcException.class, () -> lb.select(List.of()));
    }

    @Test
    void randomThrowsOnNull() {
        var lb = new RandomLoadBalancer();
        assertThrows(RpcException.class, () -> lb.select(null));
    }

    // ── RoundRobinLoadBalancer ──

    @Test
    void roundRobinCyclesThroughAddresses() {
        var lb = new RoundRobinLoadBalancer();
        List<String> addrs = Arrays.asList("a", "b", "c");
        assertEquals("a", lb.select(addrs));
        assertEquals("b", lb.select(addrs));
        assertEquals("c", lb.select(addrs));
        assertEquals("a", lb.select(addrs)); // wraps around
    }

    @Test
    void roundRobinSingleAddress() {
        var lb = new RoundRobinLoadBalancer();
        List<String> addrs = List.of("only");
        assertEquals("only", lb.select(addrs));
        assertEquals("only", lb.select(addrs));
    }

    @Test
    void roundRobinThrowsOnEmpty() {
        var lb = new RoundRobinLoadBalancer();
        assertThrows(RpcException.class, () -> lb.select(List.of()));
    }

    @Test
    void roundRobinThrowsOnNull() {
        var lb = new RoundRobinLoadBalancer();
        assertThrows(RpcException.class, () -> lb.select(null));
    }

    @Test
    void roundRobinHandlesSizeChanges() {
        var lb = new RoundRobinLoadBalancer();
        // Start with 3 addresses
        List<String> three = Arrays.asList("a", "b", "c");
        lb.select(three); // index 0 -> a
        lb.select(three); // index 1 -> b

        // Switch to 2 addresses
        List<String> two = Arrays.asList("x", "y");
        String result = lb.select(two); // index 2 % 2 = 0 -> x
        assertEquals("x", result);
    }
}
