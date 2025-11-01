package com.fabricmanagement.common.platform.auth.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * AuthUser entity - Authentication credentials for users.
 *
 * <p><b>CRITICAL DESIGN:</b></p>
 * <ul>
 *   <li>✅ References Contact entity (new communication system)</li>
 *   <li>✅ Password stored as BCrypt hash</li>
 *   <li>✅ Verification status tracked</li>
 *   <li>✅ Failed login attempts & lockout</li>
 *   <li>✅ Last login tracking</li>
 * </ul>
 *
 * <h2>Security Features:</h2>
 * <ul>
 *   <li>BCrypt password hashing (strength 10)</li>
 *   <li>Account lockout after 5 failed attempts (30 min)</li>
 *   <li>Must be verified before login</li>
 *   <li>Tracks last login for security audit</li>
 * </ul>
 *
 */
@Entity
@Table(name = "common_auth_user", schema = "common_auth",
    indexes = {
        @Index(name = "idx_auth_contact", columnList = "contact_id", unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser extends BaseEntity {

    /**
     * Contact entity reference (new system)
     * <p>References Contact entity from communication module.</p>
     */
    @Column(name = "contact_id", nullable = false, unique = true)
    private UUID contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", insertable = false, updatable = false)
    private com.fabricmanagement.common.platform.communication.domain.Contact contact;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * Create AuthUser with Contact entity (new system).
     */
    public static AuthUser create(UUID contactId, String passwordHash) {
        return AuthUser.builder()
            .contactId(contactId)
            .passwordHash(passwordHash)
            .isVerified(false)
            .failedLoginAttempts(0)
            .build();
    }

    /**
     * Get contact value from Contact entity.
     */
    public String getContactValue() {
        return contact != null ? contact.getContactValue() : null;
    }

    public void verify() {
        this.isVerified = true;
    }

    public void recordSuccessfulLogin() {
        this.lastLoginAt = Instant.now();
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = Instant.now().plusSeconds(1800); // 30 minutes
        }
    }

    public boolean isLocked() {
        if (this.lockedUntil == null) {
            return false;
        }
        return this.lockedUntil.isAfter(Instant.now());
    }

    public void unlock() {
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    @Override
    protected String getModuleCode() {
        return "AUTH";
    }
}

