package com.fabricmanagement.common.platform.auth.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh Token entity for JWT token refresh flow.
 *
 * <p>Enables secure token refresh without re-authentication.</p>
 *
 * <h2>Security:</h2>
 * <ul>
 *   <li>UUID-based token (not predictable)</li>
 *   <li>7-day expiry (configurable)</li>
 *   <li>Can be revoked (logout)</li>
 *   <li>One-time use (rotation on refresh)</li>
 * </ul>
 */
@Entity
@Table(name = "common_refresh_token", schema = "common_auth",
    indexes = {
        @Index(name = "idx_refresh_token", columnList = "token", unique = true),
        @Index(name = "idx_refresh_user", columnList = "user_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseEntity {

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private Boolean isRevoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static RefreshToken create(UUID userId, String token, Instant expiresAt) {
        return RefreshToken.builder()
            .userId(userId)
            .token(token)
            .expiresAt(expiresAt)
            .isRevoked(false)
            .build();
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(Instant.now());
    }

    public boolean isValid() {
        return !this.isRevoked && !isExpired();
    }

    public void revoke() {
        this.isRevoked = true;
        this.revokedAt = Instant.now();
    }
}

