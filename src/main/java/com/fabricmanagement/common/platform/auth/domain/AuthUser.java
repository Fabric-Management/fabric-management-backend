package com.fabricmanagement.common.platform.auth.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * AuthUser entity - Authentication credentials for users.
 *
 * <p><b>CRITICAL DESIGN CHANGE:</b></p>
 * <ul>
 *   <li>✅ User-based authentication (one AuthUser per User)</li>
 *   <li>✅ Multi-contact login supported (any verified contact can login)</li>
 *   <li>✅ Password stored as BCrypt hash</li>
 *   <li>✅ Verification status tracked</li>
 *   <li>✅ Failed login attempts & lockout</li>
 *   <li>✅ Last login tracking</li>
 * </ul>
 *
 * <h2>Design Benefits:</h2>
 * <ul>
 *   <li>Single password record per user (no duplication)</li>
 *   <li>Simpler password management</li>
 *   <li>Better performance (less data, faster queries)</li>
 *   <li>Easier tenant isolation</li>
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
        @Index(name = "idx_auth_user", columnList = "user_id", unique = true),
        @Index(name = "idx_auth_contact", columnList = "contact_id")  // Non-unique for backward compatibility
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser extends BaseEntity {

    /**
     * User entity reference (new user-based authentication).
     * <p>One AuthUser per User. Any verified contact of this User can be used for login.</p>
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private com.fabricmanagement.common.platform.user.domain.User user;

    /**
     * Contact entity reference (DEPRECATED - kept for backward compatibility).
     * <p>This field is deprecated. Use userId instead.</p>
     * <p>Will be removed in future migration.</p>
     */
    @Deprecated
    @Column(name = "contact_id")
    private UUID contactId;

    @Deprecated
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
     * Create AuthUser with User entity (new user-based authentication).
     * 
     * @param userId User ID
     * @param passwordHash BCrypt hashed password
     * @return New AuthUser instance
     */
    public static AuthUser create(UUID userId, String passwordHash) {
        return AuthUser.builder()
            .userId(userId)
            .passwordHash(passwordHash)
            .isVerified(false)
            .failedLoginAttempts(0)
            .build();
    }

    /**
     * Create AuthUser with Contact entity (DEPRECATED - for backward compatibility).
     * 
     * @deprecated Use {@link #create(UUID, String)} with userId instead
     */
    @Deprecated
    public static AuthUser createWithContact(UUID contactId, String passwordHash) {
        return AuthUser.builder()
            .contactId(contactId)
            .passwordHash(passwordHash)
            .isVerified(false)
            .failedLoginAttempts(0)
            .build();
    }

    /**
     * Get contact value from Contact entity (DEPRECATED).
     * 
     * @deprecated Use User's contacts via UserContactService instead
     */
    @Deprecated
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

