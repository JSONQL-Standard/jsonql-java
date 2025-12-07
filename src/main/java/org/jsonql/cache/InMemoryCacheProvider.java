package org.jsonql.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCacheProvider implements CacheProvider {
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiry) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    @Override
    public void set(String key, Object value, int ttlSeconds) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + (ttlSeconds * 1000L)));
    }

    private static class CacheEntry {
        Object value;
        long expiry;

        CacheEntry(Object value, long expiry) {
            this.value = value;
            this.expiry = expiry;
        }
    }
}
