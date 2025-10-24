package com.fabricmanagement.common.platform.auth.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.platform.user.domain.ContactType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * AuthUser entity - Authentication credentials for users.
 *
 * <p><b>CRITICAL DESIGN:</b></p>
 * <ul>
 *   <li>❌ NO username field - Use contactValue (email/phone)</li>
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
 */
@Entity
@Table(name = "common_auth_user", schema = "common_auth",
    indexes = {
        @Index(name = "idx_auth_contact", columnList = "contact_value", unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser extends BaseEntity {

    @Column(name = "contact_value", nullable = false, unique = true, length = 255)
    private String contactValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 20)
    private ContactType contactType;

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

    public static AuthUser create(String contactValue, ContactType contactType, String passwordHash) {
        return AuthUser.builder()
            .contactValue(contactValue)
            .contactType(contactType)
            .passwordHash(passwordHash)
            .isVerified(false)
            .failedLoginAttempts(0)
            .build();
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
}

