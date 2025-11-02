package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.platform.ai.config.AIProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Response Cache - Reduces LLM calls and costs by caching frequent queries.
 *
 * <p>Simple in-memory cache with TTL. Cache key: userId + normalizedQuery</p>
 * <p>MANIFESTO: KISS - Simple cache, no over-engineering</p>
 */
@Component
@Slf4j
public class AICache {

    private final AIProperties aiProperties;
    
    public AICache(AIProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    private long getCacheTtlSeconds() {
        return aiProperties.getCacheTtlSeconds() != null 
            ? aiProperties.getCacheTtlSeconds() 
            : 300; // Default 5 minutes
    }
    
    private record CacheEntry(String response, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    // Cache: cacheKey -> CacheEntry
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Get cached response if available and not expired.
     *
     * @param userId user ID
     * @param query user query (normalized)
     * @return cached response if found and valid
     */
    public Optional<String> get(UUID userId, String query) {
        if (!aiProperties.getCacheEnabled()) {
            return Optional.empty(); // Cache disabled
        }
        
        String key = buildKey(userId, query);
        CacheEntry entry = cache.get(key);
        
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                cache.remove(key); // Remove expired entry
            }
            return Optional.empty();
        }
        
        log.debug("Cache HIT: userId={}, query={}", userId, query);
        return Optional.of(entry.response());
    }

    /**
     * Cache a response.
     *
     * @param userId user ID
     * @param query user query (normalized)
     * @param response AI response
     */
    public void put(UUID userId, String query, String response) {
        if (!aiProperties.getCacheEnabled()) {
            return; // Cache disabled
        }
        
        String key = buildKey(userId, query);
        Instant expiresAt = Instant.now().plusSeconds(getCacheTtlSeconds());
        cache.put(key, new CacheEntry(response, expiresAt));
        
        log.debug("Cache PUT: userId={}, query={}, expiresAt={}", userId, query, expiresAt);
        
        // Cleanup expired entries periodically (simple cleanup on put)
        cleanup();
    }

    /**
     * Clear cache for a user (or all if userId is null).
     */
    public void clear(UUID userId) {
        if (userId == null) {
            cache.clear();
            log.debug("Cache cleared (all)");
        } else {
            cache.entrySet().removeIf(entry -> entry.getKey().startsWith(userId.toString() + ":"));
            log.debug("Cache cleared for userId={}", userId);
        }
    }

    /**
     * Build cache key from userId and normalized query.
     */
    private String buildKey(UUID userId, String query) {
        String normalizedQuery = normalizeQuery(query);
        return userId != null ? userId + ":" + normalizedQuery : "anonymous:" + normalizedQuery;
    }

    /**
     * Normalize query for cache key (lowercase, trim, remove extra spaces).
     */
    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * Remove expired entries (simple cleanup, not too frequent).
     */
    private void cleanup() {
        // Cleanup every 10% of cache size to avoid overhead
        if (cache.size() % 10 == 0) {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            log.debug("Cache cleanup: removed expired entries");
        }
    }

    /**
     * Get cache stats for monitoring.
     */
    public Map<String, Object> getStats() {
        long expiredCount = cache.values().stream()
            .filter(CacheEntry::isExpired)
            .count();
        
        return Map.of(
            "size", cache.size(),
            "expired", expiredCount,
            "active", cache.size() - expiredCount
        );
    }
}

