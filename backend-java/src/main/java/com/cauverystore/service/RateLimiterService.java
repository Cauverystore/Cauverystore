package com.cauverystore.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean allow(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        String mapKey = key + "|" + (now / windowMillis);
        Bucket bucket = buckets.computeIfAbsent(mapKey, k -> new Bucket(now));
        long added = bucket.count.incrementAndGet();
        if (added > maxRequests) {
            return false;
        }
        evictIfNeeded(now);
        return true;
    }

    private void evictIfNeeded(long now) {
        if (buckets.size() > 100000) {
            buckets.entrySet().removeIf(e -> now - e.getValue().startedAt > 3600_000L);
            if (buckets.size() > 100000) {
                buckets.clear();
            }
        }
    }

    private static final class Bucket {
        private final AtomicInteger count = new AtomicInteger();
        private final long startedAt;

        private Bucket(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
