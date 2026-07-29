package com.example.logmonitor.common;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int capacity, int tokensPerSecond) {
        long now = System.currentTimeMillis();
        TokenBucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null) {
                return new TokenBucket(capacity, tokensPerSecond, now);
            }
            existing.refill(now);
            return existing;
        });

        // Periodic cleanup of idle buckets
        if (buckets.size() > 5000) {
            buckets.entrySet().removeIf(entry -> now - entry.getValue().lastRefillMs > 600000);
        }

        return bucket.tryConsume();
    }

    private static class TokenBucket {
        private final int capacity;
        private final int tokensPerSecond;
        private double tokens;
        private long lastRefillMs;

        public TokenBucket(int capacity, int tokensPerSecond, long now) {
            this.capacity = capacity;
            this.tokensPerSecond = tokensPerSecond;
            this.tokens = capacity;
            this.lastRefillMs = now;
        }

        public synchronized void refill(long now) {
            long elapsedMs = now - lastRefillMs;
            if (elapsedMs > 0) {
                double addedTokens = (elapsedMs / 1000.0) * tokensPerSecond;
                this.tokens = Math.min(capacity, this.tokens + addedTokens);
                this.lastRefillMs = now;
            }
        }

        public synchronized boolean tryConsume() {
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
