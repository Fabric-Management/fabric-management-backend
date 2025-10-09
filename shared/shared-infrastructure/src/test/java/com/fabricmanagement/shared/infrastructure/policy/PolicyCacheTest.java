package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.domain.policy.PolicyDecision;
import com.fabricmanagement.shared.infrastructure.policy.cache.PolicyCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PolicyCache
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@DisplayName("PolicyCache Tests")
class PolicyCacheTest {
    
    private PolicyCache cache;
    
    @BeforeEach
    void setUp() {
        cache = new PolicyCache();
        cache.clear(); // Ensure clean state
    }
    
    @Test
    @DisplayName("Should return null for cache miss")
    void shouldReturnNullForCacheMiss() {
        // When
        PolicyDecision result = cache.get("non-existent-key");
        
        // Then
        assertNull(result);
    }
    
    @Test
    @DisplayName("Should return cached decision for cache hit")
    void shouldReturnCachedDecision() {
        // Given
        String key = "user123::/api/users::READ";
        PolicyDecision decision = PolicyDecision.allow("test_reason", "v1.0", "corr-123");
        cache.put(key, decision);
        
        // When
        PolicyDecision result = cache.get(key);
        
        // Then
        assertNotNull(result);
        assertEquals(decision.getReason(), result.getReason());
        assertEquals(decision.getCorrelationId(), result.getCorrelationId());
    }
    
    @Test
    @DisplayName("Should not cache null key")
    void shouldNotCacheNullKey() {
        // Given
        PolicyDecision decision = PolicyDecision.allow("test", "v1.0", "corr-123");
        
        // When
        cache.put(null, decision);
        
        // Then
        assertEquals(0, cache.getStats().get("size"));
    }
    
    @Test
    @DisplayName("Should not cache null decision")
    void shouldNotCacheNullDecision() {
        // When
        cache.put("test-key", null);
        
        // Then
        assertEquals(0, cache.getStats().get("size"));
    }
    
    @Test
    @DisplayName("Should evict specific key")
    void shouldEvictSpecificKey() {
        // Given
        String key = "user123::/api/users::READ";
        PolicyDecision decision = PolicyDecision.allow("test", "v1.0", "corr-123");
        cache.put(key, decision);
        
        // When
        cache.evict(key);
        
        // Then
        assertNull(cache.get(key));
    }
    
    @Test
    @DisplayName("Should evict all entries for user")
    void shouldEvictAllEntriesForUser() {
        // Given
        String userId = "user123";
        cache.put(userId + "::/api/users::READ", PolicyDecision.allow("test1", "v1.0", "c1"));
        cache.put(userId + "::/api/contacts::READ", PolicyDecision.allow("test2", "v1.0", "c2"));
        cache.put("user456::/api/users::READ", PolicyDecision.allow("test3", "v1.0", "c3"));
        
        // When
        cache.evictUser(userId);
        
        // Then
        assertNull(cache.get(userId + "::/api/users::READ"));
        assertNull(cache.get(userId + "::/api/contacts::READ"));
        assertNotNull(cache.get("user456::/api/users::READ")); // Other user's entry should remain
    }
    
    @Test
    @DisplayName("Should evict all entries for endpoint")
    void shouldEvictAllEntriesForEndpoint() {
        // Given
        String endpoint = "/api/users";
        cache.put("user123::" + endpoint + "::READ", PolicyDecision.allow("test1", "v1.0", "c1"));
        cache.put("user456::" + endpoint + "::WRITE", PolicyDecision.allow("test2", "v1.0", "c2"));
        cache.put("user123::/api/contacts::READ", PolicyDecision.allow("test3", "v1.0", "c3"));
        
        // When
        cache.evictEndpoint(endpoint);
        
        // Then
        assertNull(cache.get("user123::" + endpoint + "::READ"));
        assertNull(cache.get("user456::" + endpoint + "::WRITE"));
        assertNotNull(cache.get("user123::/api/contacts::READ")); // Other endpoint should remain
    }
    
    @Test
    @DisplayName("Should clear entire cache")
    void shouldClearEntireCache() {
        // Given
        cache.put("key1", PolicyDecision.allow("test1", "v1.0", "c1"));
        cache.put("key2", PolicyDecision.allow("test2", "v1.0", "c2"));
        cache.put("key3", PolicyDecision.allow("test3", "v1.0", "c3"));
        
        // When
        cache.clear();
        
        // Then
        assertEquals(0, cache.getStats().get("size"));
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertNull(cache.get("key3"));
    }
    
    @Test
    @DisplayName("Should build correct cache key")
    void shouldBuildCorrectCacheKey() {
        // When
        String key = cache.buildKey("user123", "/api/users", "READ");
        
        // Then
        assertEquals("user123::/api/users::READ", key);
    }
    
    @Test
    @DisplayName("Should return null for cache key with null parameters")
    void shouldReturnNullForKeyWithNullParams() {
        // When
        String key1 = cache.buildKey(null, "/api/users", "READ");
        String key2 = cache.buildKey("user123", null, "READ");
        String key3 = cache.buildKey("user123", "/api/users", null);
        
        // Then
        assertNull(key1);
        assertNull(key2);
        assertNull(key3);
    }
    
    @Test
    @DisplayName("Should report correct stats")
    void shouldReportCorrectStats() {
        // Given
        cache.put("key1", PolicyDecision.allow("test1", "v1.0", "c1"));
        cache.put("key2", PolicyDecision.allow("test2", "v1.0", "c2"));
        
        // When
        var stats = cache.getStats();
        
        // Then
        assertEquals(2, stats.get("size"));
        assertEquals("in-memory", stats.get("type"));
        assertEquals(5, stats.get("ttl_minutes"));
    }
    
    @Test
    @DisplayName("Should report healthy status")
    void shouldReportHealthyStatus() {
        // When
        boolean healthy = cache.isHealthy();
        
        // Then
        assertTrue(healthy);
    }
}

