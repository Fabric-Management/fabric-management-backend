package com.fabricmanagement.auth.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.shared.domain.role.SystemRole;
import com.fabricmanagement.shared.domain.valueobject.ContactType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Auth User Aggregate
 * 
 * Authentication-specific user data (separate from user-service)
 * Contains only authentication and authorization related fields
 * 
 * ⚠️ NO USERNAME FIELD - Authentication uses contactValue (email/phone) from Contact Service
 * 
 * Pattern: "Schema local, standard global"
 * - Owns auth-specific data
 * - References user-service for profile data
 * - Extends BaseEntity for UUID id, audit fields, soft delete
 */
@Entity
@Table(name = "auth_users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AuthUser extends BaseEntity {

    @Column(name = "contact_value", unique = true, nullable = false, length = 100)
    private String contactValue;

    @Column(name = "contact_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ContactType contactType;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "salt", nullable = false)
    private String salt;

    @Column(name = "is_active", nullable = false)
    @lombok.Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_locked", nullable = false)
    @lombok.Builder.Default
    private Boolean isLocked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    @lombok.Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_failed_login")
    private LocalDateTime lastFailedLogin;

    @Column(name = "last_successful_login")
    private LocalDateTime lastSuccessfulLogin;

    @Column(name = "password_changed_at", nullable = false)
    @lombok.Builder.Default
    private LocalDateTime passwordChangedAt = LocalDateTime.now();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @lombok.Builder.Default
    private SystemRole role = SystemRole.USER;

    // =========================================================================
    // BUSINESS METHODS
    // =========================================================================

    /**
     * Increment failed login attempts
     */
    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        this.lastFailedLogin = LocalDateTime.now();
    }

    /**
     * Reset failed login attempts on successful login
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lastSuccessfulLogin = LocalDateTime.now();
    }

    /**
     * Lock user account
     */
    public void lockAccount() {
        this.isLocked = true;
    }

    /**
     * Unlock user account
     */
    public void unlockAccount() {
        this.isLocked = false;
        this.failedLoginAttempts = 0;
    }

    /**
     * Check if account is locked
     */
    public boolean isAccountLocked() {
        return this.isLocked || !this.isActive;
    }

    /**
     * Update password
     */
    public void updatePassword(String passwordHash, String salt) {
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.passwordChangedAt = LocalDateTime.now();
    }
}
