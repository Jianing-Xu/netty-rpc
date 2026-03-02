package com.xujn.nettyrpc.application.client;

import com.xujn.nettyrpc.domain.model.RpcResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class PendingRequestsTest {

    private PendingRequests pending;

    @BeforeEach
    void setUp() {
        pending = new PendingRequests();
    }

    @Test
    void testPutAndComplete() throws Exception {
        CompletableFuture<RpcResponse> future = pending.put(1L);
        assertFalse(future.isDone());
        assertEquals(1, pending.size());

        RpcResponse response = RpcResponse.success(1L, "result");
        pending.complete(1L, response);

        assertTrue(future.isDone());
        assertEquals("result", future.get().getResult());
        assertEquals(0, pending.size());
    }

    @Test
    void testCompleteExceptionally() {
        CompletableFuture<RpcResponse> future = pending.put(2L);
        pending.completeExceptionally(2L, new RuntimeException("error"));

        assertTrue(future.isCompletedExceptionally());
        assertThrows(ExecutionException.class, future::get);
        assertEquals(0, pending.size());
    }

    @Test
    void testCompleteNonExistentRequest() {
        // Should not throw
        assertDoesNotThrow(() -> pending.complete(999L, RpcResponse.success(999L, "x")));
    }

    @Test
    void testCompleteExceptionallyNonExistent() {
        assertDoesNotThrow(() -> pending.completeExceptionally(999L, new RuntimeException()));
    }

    @Test
    void testRemove() {
        pending.put(3L);
        assertEquals(1, pending.size());
        pending.remove(3L);
        assertEquals(0, pending.size());
    }

    @Test
    void testMultiplePendingRequests() throws Exception {
        CompletableFuture<RpcResponse> f1 = pending.put(1L);
        CompletableFuture<RpcResponse> f2 = pending.put(2L);
        CompletableFuture<RpcResponse> f3 = pending.put(3L);
        assertEquals(3, pending.size());

        pending.complete(2L, RpcResponse.success(2L, "two"));
        assertFalse(f1.isDone());
        assertTrue(f2.isDone());
        assertFalse(f3.isDone());
        assertEquals("two", f2.get().getResult());
        assertEquals(2, pending.size());
    }

    @Test
    void testTimeoutOnIncompleteRequest() {
        CompletableFuture<RpcResponse> future = pending.put(4L);
        assertThrows(TimeoutException.class,
                () -> future.get(50, TimeUnit.MILLISECONDS));
    }
}
