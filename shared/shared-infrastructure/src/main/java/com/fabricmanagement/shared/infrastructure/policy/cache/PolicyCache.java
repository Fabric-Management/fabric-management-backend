package com.fabricmanagement.shared.infrastructure.policy.cache;

import com.fabricmanagement.shared.domain.policy.PolicyDecision;
import com.fabricmanagement.shared.infrastructure.policy.constants.PolicyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Policy Cache
 * 
 * Caches policy decisions to improve performance.
 * Reduces PDP evaluation latency for repeated requests.
 * 
 * Cache Strategy:
 * - Key: userId + endpoint + operation
 * - TTL: 5 minutes (configurable)
 * - Eviction: On policy change events
 * - Storage: Redis (for production) or in-memory (for testing)
 * 
 * Performance Benefits:
 * - Cache hit: ~1-2ms (vs ~30-50ms for full evaluation)
 * - Target cache hit rate: >90%
 * - Reduces database load
 * 
 * Cache Invalidation:
 * - Automatic expiry (TTL)
 * - Manual eviction (policy updates)
 * - Kafka event-driven (PolicyUpdatedEvent)
 * 
 * Design Principles:
 * - Thread-safe (ConcurrentHashMap)
 * - Fail-safe (cache miss = re-evaluate)
 * - Observable (metrics for hit/miss rate)
 * 
 * TODO: Replace in-memory cache with Redis for production
 * 
 * Usage:
 * <pre>
 * String cacheKey = policyCache.buildKey(context);
 * PolicyDecision cached = policyCache.get(cacheKey);
 * if (cached != null && !cached.isExpired(5)) {
 *     return cached; // Cache hit
 * }
 * 
 * PolicyDecision decision = policyEngine.evaluate(context);
 * policyCache.put(cacheKey, decision);
 * return decision;
 * </pre>
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Slf4j
@Component
public class PolicyCache {
    
    private static final int DEFAULT_TTL_MINUTES = PolicyConstants.CACHE_TTL_MINUTES;
    
    // TODO: Replace with Redis for production
    // In-memory cache for development/testing
    private final Map<String, PolicyDecision> cache = new ConcurrentHashMap<>();
    
    /**
     * Get cached decision
     * 
     * @param cacheKey cache key
     * @return cached decision or null if not found/expired
     */
    public PolicyDecision get(String cacheKey) {
        PolicyDecision cached = cache.get(cacheKey);
        
        if (cached == null) {
            log.debug("Cache MISS for key: {}", cacheKey);
            return null;
        }
        
        // Check if expired
        if (cached.isExpired(DEFAULT_TTL_MINUTES)) {
            log.debug("Cache EXPIRED for key: {}", cacheKey);
            cache.remove(cacheKey); // Remove expired entry
            return null;
        }
        
        log.debug("Cache HIT for key: {}", cacheKey);
        return cached;
    }
    
    /**
     * Put decision in cache
     * 
     * @param cacheKey cache key
     * @param decision policy decision
     */
    public void put(String cacheKey, PolicyDecision decision) {
        if (cacheKey == null || decision == null) {
            log.warn("Cannot cache null key or decision");
            return;
        }
        
        cache.put(cacheKey, decision);
        log.debug("Cache PUT for key: {} (decision: {})", 
            cacheKey, decision.isAllowed() ? "ALLOW" : "DENY");
    }
    
    /**
     * Evict specific cache entry
     * 
     * @param cacheKey cache key
     */
    public void evict(String cacheKey) {
        PolicyDecision removed = cache.remove(cacheKey);
        if (removed != null) {
            log.info("Cache EVICT for key: {}", cacheKey);
        }
    }
    
    /**
     * Evict all cache entries for user
     * 
     * @param userId user ID
     */
    public void evictUser(String userId) {
        if (userId == null) {
            return;
        }
        
        int evicted = 0;
        for (String key : cache.keySet()) {
            if (key.startsWith(userId + ":")) {
                cache.remove(key);
                evicted++;
            }
        }
        
        if (evicted > 0) {
            log.info("Cache EVICT for user: {} ({} entries removed)", userId, evicted);
        }
    }
    
    /**
     * Evict all cache entries for endpoint
     * 
     * @param endpoint API endpoint
     */
    public void evictEndpoint(String endpoint) {
        if (endpoint == null) {
            return;
        }
        
        int evicted = 0;
        for (String key : cache.keySet()) {
            if (key.contains(":" + endpoint + ":")) {
                cache.remove(key);
                evicted++;
            }
        }
        
        if (evicted > 0) {
            log.info("Cache EVICT for endpoint: {} ({} entries removed)", endpoint, evicted);
        }
    }
    
    /**
     * Clear entire cache
     * Used for policy version updates or emergency situations
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.warn("Cache CLEAR - {} entries removed", size);
    }
    
    /**
     * Build cache key from context
     * 
     * Format: userId:endpoint:operation
     * Example: "550e8400::/api/users/{id}::WRITE"
     * 
     * @param userId user ID
     * @param endpoint API endpoint
     * @param operation operation type
     * @return cache key
     */
    public String buildKey(String userId, String endpoint, String operation) {
        if (userId == null || endpoint == null || operation == null) {
            log.warn("Cannot build cache key with null parameters");
            return null;
        }
        
        return String.format("%s%s%s%s%s", 
            userId, 
            PolicyConstants.CACHE_KEY_SEPARATOR, 
            endpoint, 
            PolicyConstants.CACHE_KEY_SEPARATOR, 
            operation);
    }
    
    /**
     * Get cache statistics
     * 
     * @return map with cache stats
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "size", cache.size(),
            "type", "in-memory",
            "ttl_minutes", DEFAULT_TTL_MINUTES
        );
    }
    
    /**
     * Check if cache is healthy
     * 
     * @return true if cache is operational
     */
    public boolean isHealthy() {
        // For in-memory cache, always healthy
        // TODO: For Redis, check connection
        return true;
    }
}

