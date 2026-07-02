package com.autoresolve.mediabuying.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Spring {@link Service} implementation of {@link CacheService} backed by a
 * {@link ConcurrentHashMap}. Each entry carries an expiry timestamp; expired
 * entries are skipped on read and lazily evicted on write.
 */
@Service
public class NativeCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(NativeCacheService.class);

    /**
     * Internal wrapper that holds the cached value together with the epoch-millis
     * at which it expires.
     */
    private static final class CacheEntry {
        final Object value;
        final long expiryEpochMs;

        CacheEntry(Object value, long ttlMillis) {
            this.value = value;
            this.expiryEpochMs = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiryEpochMs;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            log.trace("Cache entry expired for key={}", key);
            store.remove(key);
            return null;
        }
        return (T) entry.value;
    }

    @Override
    public void put(String key, Object value, long ttlMillis) {
        if (ttlMillis <= 0) {
            log.warn("Ignoring cache put with non-positive TTL ({}) for key={}", ttlMillis, key);
            return;
        }
        store.put(key, new CacheEntry(value, ttlMillis));
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public void deleteByPattern(String pattern) {
        Pattern regex = globToRegex(pattern);
        store.keySet().removeIf(k -> regex.matcher(k).matches());
    }

    @Override
    public Set<String> keys(String pattern) {
        Pattern regex = globToRegex(pattern);
        Set<String> matching = new HashSet<>();
        for (String key : store.keySet()) {
            if (regex.matcher(key).matches()) {
                matching.add(key);
            }
        }
        return matching;
    }

    /**
     * Converts a simple glob pattern (where {@code *} matches any sequence of
     * characters) into a {@link Pattern}. All other regex metacharacters are
     * escaped so they are treated as literals.
     */
    static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString());
    }
}
