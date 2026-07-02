package com.autoresolve.mediabuying.cache;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Simple cache abstraction backed by a ConcurrentHashMap.
 * Supports put/get/delete by key, pattern-based key scan, and TTL expiration.
 */
public interface CacheService {
    <T> T get(String key);
    void put(String key, Object value, long ttlMillis);
    void delete(String key);
    void deleteByPattern(String pattern);
    Set<String> keys(String pattern);
}
