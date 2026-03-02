package com.xujn.nettyrpc.core.ratelimit;

import com.xujn.nettyrpc.api.ratelimit.RateLimiter;

/**
 * A basic Token Bucket Rate Limiter implementation.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final int rate; // tokens per second
    private int tokens;
    private long lastRefillTimestamp;

    public TokenBucketRateLimiter(int capacity, int rate) {
        this.capacity = capacity;
        this.rate = rate;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        if (now > lastRefillTimestamp) {
            long generatedTokens = (now - lastRefillTimestamp) * rate / 1000;
            if (generatedTokens > 0) {
                tokens = (int) Math.min(capacity, tokens + generatedTokens);
                lastRefillTimestamp = now;
            }
        }
    }
}
