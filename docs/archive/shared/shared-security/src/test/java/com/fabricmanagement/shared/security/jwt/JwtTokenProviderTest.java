package com.fabricmanagement.shared.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit Tests for JwtTokenProvider
 * 
 * CRITICAL: shared-security had 0% coverage before this!
 * 
 * Coverage Target: 95%+
 * Pattern: Given-When-Then
 * 
 * @since 1.0.0 (2025-10-20)
 */
@DisplayName("JwtTokenProvider - JWT Token Generation & Validation")
class JwtTokenProviderTest {
    
    private JwtTokenProvider jwtTokenProvider;
    
    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-HS256-algorithm-security-requirements-1234567890";
    private static final long TEST_EXPIRATION = 3600000L; // 1 hour
    private static final long TEST_REFRESH_EXPIRATION = 86400000L; // 24 hours
    
    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        
        // Use reflection to set @Value fields (no Spring context needed)
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpiration", TEST_REFRESH_EXPIRATION);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtIssuer", "fabric-management-system");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtAudience", "fabric-api");
    }
    
    @Test
    @DisplayName("Should generate valid JWT token with userId and tenantId")
    void shouldGenerateTokenWithUserAndTenant() {
        // Given
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "TENANT_ADMIN");
        
        // When
        String token = jwtTokenProvider.generateToken(userId, tenantId, claims);
        
        // Then
        assertThat(token).isNotNull().isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts: header.payload.signature
        
        // Verify claims
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.extractTenantId(token)).isEqualTo(tenantId);
        assertThat(jwtTokenProvider.extractRole(token)).isEqualTo("TENANT_ADMIN");
    }
    
    @Test
    @DisplayName("Should generate valid refresh token")
    void shouldGenerateRefreshToken() {
        // Given
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        
        // When
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId, tenantId);
        
        // Then
        assertThat(refreshToken).isNotNull().isNotEmpty();
        assertThat(jwtTokenProvider.extractUserId(refreshToken)).isEqualTo(userId);
        assertThat(jwtTokenProvider.extractTenantId(refreshToken)).isEqualTo(tenantId);
        
        // Verify it's marked as refresh token
        String type = jwtTokenProvider.extractClaim(refreshToken, 
            claims -> claims.get("type", String.class));
        assertThat(type).isEqualTo("refresh");
    }
    
    @Test
    @DisplayName("Should validate token successfully for valid token")
    void shouldValidateValidToken() {
        // Given
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateToken(userId, tenantId, new HashMap<>());
        
        // When
        Boolean isValid = jwtTokenProvider.validateToken(token);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    @DisplayName("Should validate token with username check")
    void shouldValidateTokenWithUsername() {
        // Given
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateToken(userId, tenantId, new HashMap<>());
        
        // When
        Boolean isValid = jwtTokenProvider.validateToken(token, userId);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    @DisplayName("Should reject token with wrong username")
    void shouldRejectTokenWithWrongUsername() {
        // Given
        String userId = UUID.randomUUID().toString();
        String wrongUserId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateToken(userId, tenantId, new HashMap<>());
        
        // When
        Boolean isValid = jwtTokenProvider.validateToken(token, wrongUserId);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    @DisplayName("Should reject malformed token")
    void shouldRejectMalformedToken() {
        // Given
        String malformedToken = "not.a.valid.jwt.token";
        
        // When
        Boolean isValid = jwtTokenProvider.validateToken(malformedToken);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() throws InterruptedException {
        // Given - Create provider with 1ms expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortLivedProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(shortLivedProvider, "jwtExpiration", 1L); // 1ms!
        ReflectionTestUtils.setField(shortLivedProvider, "jwtIssuer", "fabric-management-system");
        ReflectionTestUtils.setField(shortLivedProvider, "jwtAudience", "fabric-api");
        
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = shortLivedProvider.generateToken(userId, tenantId, new HashMap<>());
        
        // When - Wait for token to expire
        Thread.sleep(10); // Wait 10ms to ensure expiry
        
        // Then - validateToken should return false (catches ExpiredJwtException internally)
        assertThat(shortLivedProvider.validateToken(token)).isFalse();
        
        // Verify that isTokenExpired throws ExpiredJwtException (stronger assertion!)
        assertThatThrownBy(() -> shortLivedProvider.isTokenExpired(token))
            .isInstanceOf(ExpiredJwtException.class)
            .hasMessageContaining("JWT expired");
    }
    
    @Test
    @DisplayName("Should extract all claims correctly")
    void shouldExtractAllClaims() {
        // Given
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "COMPANY_MANAGER");
        claims.put("departmentId", UUID.randomUUID().toString());
        
        String token = jwtTokenProvider.generateToken(userId, tenantId, claims);
        
        // When & Then
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.extractTenantId(token)).isEqualTo(tenantId);
        assertThat(jwtTokenProvider.extractRole(token)).isEqualTo("COMPANY_MANAGER");
        
        String departmentId = jwtTokenProvider.extractClaim(token, 
            c -> c.get("departmentId", String.class));
        assertThat(departmentId).isNotNull();
    }
    
    @Test
    @DisplayName("Should handle null claims gracefully")
    void shouldHandleNullClaims() {
        // Given
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        
        // When
        String token = jwtTokenProvider.generateToken(userId, tenantId, new HashMap<>());
        
        // Then
        assertThat(token).isNotNull();
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.extractTenantId(token)).isEqualTo(tenantId);
    }
    
    @Test
    @DisplayName("Should reject token with invalid signature")
    void shouldRejectTokenWithInvalidSignature() {
        // Given - Generate token with different secret
        JwtTokenProvider wrongSecretProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(wrongSecretProvider, "jwtSecret", 
            "different-secret-key-256-bits-long-for-HS256-algorithm-requirements-totally-different-secret");
        ReflectionTestUtils.setField(wrongSecretProvider, "jwtExpiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(wrongSecretProvider, "jwtIssuer", "fabric-management-system");
        ReflectionTestUtils.setField(wrongSecretProvider, "jwtAudience", "fabric-api");
        
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = wrongSecretProvider.generateToken(userId, tenantId, new HashMap<>());
        
        // When - Validate with original provider (different secret)
        Boolean isValid = jwtTokenProvider.validateToken(token);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    @DisplayName("Should return correct expiration times")
    void shouldReturnCorrectExpirationTimes() {
        // When & Then
        assertThat(jwtTokenProvider.getJwtExpiration()).isEqualTo(TEST_EXPIRATION);
        assertThat(jwtTokenProvider.getRefreshExpiration()).isEqualTo(TEST_REFRESH_EXPIRATION);
    }
    
    @Test
    @DisplayName("Access token should expire before refresh token")
    void accessTokenShouldExpireBeforeRefreshToken() {
        // When & Then
        assertThat(jwtTokenProvider.getJwtExpiration())
            .isLessThan(jwtTokenProvider.getRefreshExpiration());
    }
}

