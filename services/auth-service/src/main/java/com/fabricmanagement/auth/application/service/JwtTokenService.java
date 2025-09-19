package com.fabricmanagement.auth.application.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for JWT token management.
 * Handles token creation, validation, and refresh operations.
 */
@Service
@Slf4j
public class JwtTokenService {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration:3600}")
    private int jwtExpirationInSeconds;
    
    @Value("${jwt.refresh.expiration:86400}")
    private int refreshTokenExpirationInSeconds;
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
    
    /**
     * Creates a JWT access token.
     */
    public String createAccessToken(UUID userId, String username, String email, String role, UUID tenantId) {
        log.debug("Creating access token for user: {}", userId);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("username", username);
        claims.put("email", email);
        claims.put("role", role);
        claims.put("tenantId", tenantId.toString());
        claims.put("tokenType", "access");
        
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(username)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationInSeconds * 1000L))
            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
            .compact();
    }
    
    /**
     * Creates a JWT refresh token.
     */
    public String createRefreshToken(UUID userId, String username) {
        log.debug("Creating refresh token for user: {}", userId);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("username", username);
        claims.put("tokenType", "refresh");
        
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(username)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpirationInSeconds * 1000L))
            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
            .compact();
    }
    
    /**
     * Validates a JWT token.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts username from JWT token.
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts user ID from JWT token.
     */
    public UUID getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return UUID.fromString(claims.get("userId", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Error extracting user ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts role from JWT token.
     */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return claims.get("role", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Error extracting role from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts tenant ID from JWT token.
     */
    public UUID getTenantIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return UUID.fromString(claims.get("tenantId", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Error extracting tenant ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts token type from JWT token.
     */
    public String getTokenTypeFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return claims.get("tokenType", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Error extracting token type from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if token is expired.
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }
    
    /**
     * Gets token expiration date.
     */
    public LocalDateTime getTokenExpiration(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            return claims.getExpiration().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Error getting token expiration: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Refreshes an access token using refresh token.
     */
    public String refreshAccessToken(String refreshToken) {
        log.debug("Refreshing access token");
        
        if (!validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }
        
        String tokenType = getTokenTypeFromToken(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Invalid token type for refresh");
        }
        
        if (isTokenExpired(refreshToken)) {
            throw new RuntimeException("Refresh token expired");
        }
        
        // Extract user information from refresh token
        UUID userId = getUserIdFromToken(refreshToken);
        String username = getUsernameFromToken(refreshToken);
        
        if (userId == null || username == null) {
            throw new RuntimeException("Invalid refresh token claims");
        }
        
        // Create new access token
        // Note: In a real implementation, you would fetch user details from identity-service
        return createAccessToken(userId, username, "", "USER", UUID.randomUUID());
    }
}
