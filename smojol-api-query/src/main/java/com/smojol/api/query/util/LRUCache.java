package com.smojol.api.query.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU cache thread-safe basé sur LinkedHashMap avec éviction par ordre d'accès.
 * Remplace l'ancien SimpleCache qui avait une éviction aléatoire.
 */
public class LRUCache<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(LRUCache.class);
    private final LinkedHashMap<K, V> cache;
    private final int maxSize;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long hits = 0;
    private long misses = 0;

    public LRUCache(int maxSize) {
        this.maxSize = maxSize;
        // accessOrder=true pour que le get() déplace l'entry en fin (MRU)
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean shouldRemove = size() > LRUCache.this.maxSize;
                if (shouldRemove) {
                    logger.debug("LRU evicting: {}", eldest.getKey());
                }
                return shouldRemove;
            }
        };
        logger.info("LRUCache initialized with maxSize: {}", maxSize);
    }

    public void put(K key, V value) {
        if (key == null || value == null) return;
        lock.writeLock().lock();
        try {
            cache.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<V> get(K key) {
        if (key == null) return Optional.empty();
        lock.readLock().lock();
        try {
            V value = cache.get(key);
            if (value != null) {
                hits++;
                return Optional.of(value);
            } else {
                misses++;
                return Optional.empty();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean has(K key) {
        if (key == null) return false;
        lock.readLock().lock();
        try {
            return cache.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void remove(K key) {
        if (key == null) return;
        lock.writeLock().lock();
        try {
            cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            hits = 0;
            misses = 0;
        } finally {
            lock.writeLock().unlock();
        }
        logger.info("LRUCache cleared");
    }

    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getMaxSize() {
        return maxSize;
    }

    public double getHitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    public String getStats() {
        lock.readLock().lock();
        try {
            long total = hits + misses;
            return String.format("LRUCache[size=%d/%d, hits=%d, misses=%d, hitRate=%.1f%%]",
                    cache.size(), maxSize, hits, misses, getHitRate() * 100);
        } finally {
            lock.readLock().unlock();
        }
    }
}
