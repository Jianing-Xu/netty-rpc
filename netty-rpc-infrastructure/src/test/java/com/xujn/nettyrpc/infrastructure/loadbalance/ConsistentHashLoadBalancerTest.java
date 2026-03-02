package com.xujn.nettyrpc.infrastructure.loadbalance;

import com.xujn.nettyrpc.domain.exception.RpcException;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashLoadBalancerTest {

    @Test
    void selectSingleAddress() {
        var lb = new ConsistentHashLoadBalancer();
        assertEquals("a", lb.select(List.of("a")));
    }

    @Test
    void selectDistributesAcrossAddresses() {
        var lb = new ConsistentHashLoadBalancer();
        List<String> addrs = List.of("10.0.0.1:8080", "10.0.0.2:8080", "10.0.0.3:8080");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(lb.select(addrs));
        }
        // Should select at least 2 different addresses over 100 calls
        assertTrue(seen.size() >= 2, "Expected distribution across nodes, got: " + seen);
    }

    @Test
    void selectWithKeySameKeyReturnsSameAddress() {
        var lb = new ConsistentHashLoadBalancer();
        List<String> addrs = List.of("a:8080", "b:8080", "c:8080");

        String first = lb.selectWithKey(addrs, "user-123");
        for (int i = 0; i < 50; i++) {
            assertEquals(first, lb.selectWithKey(addrs, "user-123"),
                    "Same key should consistently route to same address");
        }
    }

    @Test
    void selectWithKeyDifferentKeysDistribute() {
        var lb = new ConsistentHashLoadBalancer();
        List<String> addrs = List.of("a:8080", "b:8080", "c:8080");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(lb.selectWithKey(addrs, "key-" + i));
        }
        assertTrue(seen.size() >= 2);
    }

    @Test
    void throwsOnEmpty() {
        var lb = new ConsistentHashLoadBalancer();
        assertThrows(RpcException.class, () -> lb.select(List.of()));
    }

    @Test
    void throwsOnNull() {
        var lb = new ConsistentHashLoadBalancer();
        assertThrows(RpcException.class, () -> lb.select(null));
    }

    @Test
    void selectWithKeySingleAddress() {
        var lb = new ConsistentHashLoadBalancer();
        assertEquals("only:8080", lb.selectWithKey(List.of("only:8080"), "any-key"));
    }

    @Test
    void hashFunctionProducesPositiveValues() {
        for (int i = 0; i < 100; i++) {
            long hash = ConsistentHashLoadBalancer.hash("test-" + i);
            assertTrue(hash >= 0, "Hash should be positive");
        }
    }
}
