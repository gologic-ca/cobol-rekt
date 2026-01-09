package com.smojol.api.query.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cache simple basé sur HashMap pour les ASTs
 * Stockage temporaire des données en mémoire
 */
public class SimpleCache<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(SimpleCache.class);
    private final Map<K, V> cache = new HashMap<>();
    private final int maxSize;

    public SimpleCache(int maxSize) {
        this.maxSize = maxSize;
        logger.info("SimpleCache initialized with maxSize: {}", maxSize);
    }

    /**
     * Ajoute une entry au cache
     */
    public void put(K key, V value) {
        if (key == null || value == null) {
            logger.warn("Cannot put null key or value in cache");
            return;
        }
        
        // Si le cache est plein, supprimer une entry aléatoire
        if (cache.size() >= maxSize && !cache.containsKey(key)) {
            K keyToRemove = cache.keySet().iterator().next();
            cache.remove(keyToRemove);
            logger.debug("Cache full. Removed entry for key: {}", keyToRemove);
        }
        
        cache.put(key, value);
        logger.debug("Added to cache: {} (cache size: {})", key, cache.size());
    }

    /**
     * Récupère une entry du cache
     */
    public Optional<V> get(K key) {
        if (key == null) {
            return Optional.empty();
        }
        
        Optional<V> value = Optional.ofNullable(cache.get(key));
        if (value.isPresent()) {
            logger.debug("Cache hit for key: {}", key);
        } else {
            logger.debug("Cache miss for key: {}", key);
        }
        return value;
    }

    /**
     * Vérifie si une clé existe dans le cache
     */
    public boolean has(K key) {
        if (key == null) {
            return false;
        }
        return cache.containsKey(key);
    }

    /**
     * Supprime une entry du cache
     */
    public void remove(K key) {
        if (key == null) {
            return;
        }
        
        if (cache.remove(key) != null) {
            logger.debug("Removed from cache: {}", key);
        }
    }

    /**
     * Vide le cache complètement
     */
    public void clear() {
        cache.clear();
        logger.info("Cache cleared");
    }

    /**
     * Retourne le nombre d'entries en cache
     */
    public int size() {
        return cache.size();
    }

    /**
     * Retourne le nombre maximum d'entries
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Retourne le taux d'utilisation du cache (0.0 à 1.0)
     */
    public double getUtilization() {
        return (double) cache.size() / maxSize;
    }

    /**
     * Retourne des statistiques du cache
     */
    public String getStats() {
        return String.format("Cache[size=%d, maxSize=%d, utilization=%.1f%%]",
                cache.size(), maxSize, getUtilization() * 100);
    }

    /**
     * Retourne une copie des clés actuellement en cache
     */
    public java.util.Set<K> keySet() {
        return new java.util.HashSet<>(cache.keySet());
    }
}
