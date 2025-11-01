package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.dto.UserDto;
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
        // Get primary contact for JWT subject
        String contactValue = user.getPrimaryContact()
            .map(contact -> contact.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User has no authentication contact"));

        log.debug("Generating access token for user: {}", contactValue);

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", user.getTenantId().toString());
        claims.put("tenant_uid", extractTenantUid(user.getUid()));
        claims.put("user_id", user.getId().toString());
        claims.put("user_uid", user.getUid());
        claims.put("company_id", user.getCompanyId().toString());
        // Add first and last name
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        // Get primary department from UserDepartment junction
        String department = user.getUserDepartments().stream()
            .filter(ud -> Boolean.TRUE.equals(ud.getIsPrimary()))
            .findFirst()
            .map(ud -> ud.getDepartment().getDepartmentName())
            .orElse(null);
        claims.put("department", department);
        // Add role_id to JWT
        if (user.getRole() != null) {
            claims.put("role_id", user.getRole().getId().toString());
            claims.put("role", user.getRole().getRoleName());
        }

        return Jwts.builder()
            .subject(contactValue)
            .claims(claims)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusMillis(accessTokenExpiration)))
            .signWith(secretKey)
            .compact();
    }

    public String generateRefreshToken(User user) {
        String contactValue = user.getPrimaryContact()
            .map(contact -> contact.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User has no authentication contact"));

        log.debug("Generating refresh token for user: {}", contactValue);

        return UUID.randomUUID().toString();
    }

    /**
     * Generate access token from UserDto (overload for DTO).
     * <p>Note: UserDto should be created from User entity that has contacts/departments loaded.</p>
     *
     * @param userDto User DTO (must have contactValue and department populated from entity)
     * @return JWT access token
     */
    public String generateAccessToken(UserDto userDto) {
        // UserDto no longer has contactValue - should use User entity directly
        // This method kept for backward compatibility but should use User entity version
        throw new UnsupportedOperationException(
            "Use generateAccessToken(User) instead. UserDto no longer includes contactValue/department fields.");
    }

    /**
     * Generate refresh token from UserDto (overload for DTO).
     * <p>Note: Should use User entity directly instead.</p>
     */
    public String generateRefreshToken(UserDto userDto) {
        // UserDto no longer has contactValue - should use User entity directly
        throw new UnsupportedOperationException(
            "Use generateRefreshToken(User) instead. UserDto no longer includes contactValue field.");
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

