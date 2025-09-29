package com.fabricmanagement.identity.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Single Responsibility: JWT token provider only
 * Open/Closed: Can be extended without modification
 * Provides JWT token operations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private static final String SECRET_KEY = "mySecretKey"; // Should be in properties
    private static final long EXPIRATION_TIME = 3600000; // 1 hour

    /**
     * Generates JWT token for user.
     */
    public String generateToken(String username) {
        log.info("Generating token for user: {}", username);
        
        // Implementation would generate JWT token
        // For now, return a mock token
        return "jwt_token_" + username + "_" + System.currentTimeMillis();
    }

    /**
     * Validates JWT token.
     */
    public boolean validateToken(String token) {
        log.debug("Validating token");
        
        // Implementation would validate JWT token
        // For now, return true for valid tokens
        return token != null && token.startsWith("jwt_token_");
    }

    /**
     * Gets username from JWT token.
     */
    public String getUsernameFromToken(String token) {
        log.debug("Getting username from token");
        
        // Implementation would extract username from JWT token
        // For now, return a mock username
        return "user123";
    }

    /**
     * Gets expiration date from JWT token.
     */
    public Date getExpirationDateFromToken(String token) {
        log.debug("Getting expiration date from token");
        
        // Implementation would extract expiration date from JWT token
        // For now, return current time + 1 hour
        return new Date(System.currentTimeMillis() + EXPIRATION_TIME);
    }

    /**
     * Checks if token is expired.
     */
    public boolean isTokenExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
}