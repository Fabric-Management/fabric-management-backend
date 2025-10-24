package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.platform.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT Service - Token generation and validation.
 *
 * <p>Generates JWT tokens with comprehensive claims:
 * <ul>
 *   <li>sub: contactValue (email/phone)</li>
 *   <li>tenant_id: UUID</li>
 *   <li>tenant_uid: Human-readable</li>
 *   <li>user_id: UUID</li>
 *   <li>user_uid: Human-readable</li>
 *   <li>company_id: UUID</li>
 *   <li>department: String</li>
 * </ul>
 *
 * <h2>Token Types:</h2>
 * <ul>
 *   <li>Access aux: 15 minutes (configurable)</li>
 *   <li>Refresh Token: 7 days (configurable)</li>
 * </ul>
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtService(
            @Value("${application.jwt.secret}") String secret,
            @Value("${application.jwt.expiration:900000}") long accessTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        
        log.info("JwtService initialized - accessTokenExpiration: {}ms", accessTokenExpiration);
    }

    public String generateAccessToken(User user) {
        log.debug("Generating access token for user: {}", user.getContactValue());

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", user.getTenantId().toString());
        claims.put("tenant_uid", extractTenantUid(user.getUid()));
        claims.put("user_id", user.getId().toString());
        claims.put("user_uid", user.getUid());
        claims.put("company_id", user.getCompanyId().toString());
        claims.put("department", user.getDepartment());

        return Jwts.builder()
            .subject(user.getContactValue())
            .claims(claims)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusMillis(accessTokenExpiration)))
            .signWith(secretKey)
            .compact();
    }

    public String generateRefreshToken(User user) {
        log.debug("Generating refresh token for user: {}", user.getContactValue());

        return UUID.randomUUID().toString();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getContactValueFromToken(String token) {
        Claims claims = extractClaims(token);
        return claims.getSubject();
    }

    public UUID getTenantIdFromToken(String token) {
        Claims claims = extractClaims(token);
        String tenantId = claims.get("tenant_id", String.class);
        return UUID.fromString(tenantId);
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = extractClaims(token);
        String userId = claims.get("user_id", String.class);
        return UUID.fromString(userId);
    }

    public String getUserUidFromToken(String token) {
        Claims claims = extractClaims(token);
        return claims.get("user_uid", String.class);
    }

    public UUID getCompanyIdFromToken(String token) {
        Claims claims = extractClaims(token);
        String companyId = claims.get("company_id", String.class);
        return UUID.fromString(companyId);
    }

    public String getDepartmentFromToken(String token) {
        Claims claims = extractClaims(token);
        return claims.get("department", String.class);
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private String extractTenantUid(String userUid) {
        if (userUid == null) {
            return null;
        }
        String[] parts = userUid.split("-");
        if (parts.length >= 2) {
            return parts[0] + "-" + parts[1];
        }
        return null;
    }
}

