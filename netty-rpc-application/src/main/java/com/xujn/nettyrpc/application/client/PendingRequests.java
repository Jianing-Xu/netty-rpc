package com.xujn.nettyrpc.application.client;

import com.xujn.nettyrpc.domain.model.RpcResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping requestId -> CompletableFuture.
 * Used to correlate asynchronous Netty responses back to the calling thread.
 */
public class PendingRequests {

    private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> futureMap =
            new ConcurrentHashMap<>();

    /**
     * Register a new pending request and return a future to await.
     */
    public CompletableFuture<RpcResponse> put(long requestId) {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        futureMap.put(requestId, future);
        return future;
    }

    /**
     * Complete the pending future with the server's response.
     */
    public void complete(long requestId, RpcResponse response) {
        CompletableFuture<RpcResponse> future = futureMap.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * Complete the pending future exceptionally.
     */
    public void completeExceptionally(long requestId, Throwable cause) {
        CompletableFuture<RpcResponse> future = futureMap.remove(requestId);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    /**
     * Remove a pending request without completing it.
     */
    public void remove(long requestId) {
        futureMap.remove(requestId);
    }

    public int size() {
        return futureMap.size();
    }
}
