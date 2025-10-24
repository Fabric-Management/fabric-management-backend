package com.fabricmanagement.auth.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh Token Aggregate
 * 
 * Represents JWT refresh tokens for session management
 * Extends BaseEntity for UUID id, audit fields, soft delete
 */
@Entity
@Table(name = "auth_refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "is_revoked", nullable = false)
    @lombok.Builder.Default
    private Boolean isRevoked = false;

    @Column(name = "device_info", columnDefinition = "jsonb")
    private String deviceInfo;

    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // =========================================================================
    // BUSINESS METHODS
    // =========================================================================

    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if token is valid (not expired and not revoked)
     */
    public boolean isValid() {
        return !isExpired() && !isRevoked;
    }

    /**
     * Revoke token
     */
    public void revoke() {
        this.isRevoked = true;
    }

    /**
     * Update last used timestamp
     */
    public void updateLastUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
