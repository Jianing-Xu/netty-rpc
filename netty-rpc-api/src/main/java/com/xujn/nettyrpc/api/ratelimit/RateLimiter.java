package com.xujn.nettyrpc.api.ratelimit;

/**
 * Service Provider Interface for Server Rate Limiting.
 */
public interface RateLimiter {
    
    /**
     * Try to acquire a token/permit.
     * @return true if acquired successfully, false if rate limited.
     */
    boolean tryAcquire();
}
