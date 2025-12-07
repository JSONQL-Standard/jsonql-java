package org.jsonql.cache;

public interface CacheProvider {
    /**
     * Retrieve a value from the cache.
     * @param key The cache key.
     * @return The cached value, or null if not found or expired.
     */
    Object get(String key);

    /**
     * Store a value in the cache.
     * @param key The cache key.
     * @param value The value to store.
     * @param ttlSeconds Time-to-live in seconds.
     */
    void set(String key, Object value, int ttlSeconds);
}
