package com.fabricmanagement.user.infrastructure.persistence.entity;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import com.fabricmanagement.user.domain.valueobject.Role;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User entity focused on authentication and identity management.
 * Contact information is managed separately in contact-service.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_user_status", columnList = "status"),
    @Index(name = "idx_user_role", columnList = "role"),
    @Index(name = "idx_user_deleted", columnList = "deleted")
})
@SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ? AND version = ?")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true, exclude = "passwordHash")
@EqualsAndHashCode(callSuper = true, exclude = "passwordHash")
public class UserEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank(message = "Password is required")
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // Authentication & Security fields
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = Boolean.FALSE;

    @Column(name = "email_verification_token", length = 100)
    private String emailVerificationToken;

    @Column(name = "email_verification_sent_at")
    private LocalDateTime emailVerificationSentAt;

    @Column(name = "password_reset_token", length = 100)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expires_at")
    private LocalDateTime passwordResetTokenExpiresAt;

    @Column(name = "two_factor_enabled", nullable = false)
    @Builder.Default
    private Boolean twoFactorEnabled = Boolean.FALSE;

    @Column(name = "two_factor_secret", length = 100)
    private String twoFactorSecret;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /**
     * Checks if the user account is currently locked.
     * @return true if the account is locked, false otherwise
     */
    @Transient
    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    /**
     * Checks if the user account is active and not locked.
     * @return true if the account is active and not locked
     */
    @Transient
    public boolean isAccountActive() {
        return status == UserStatus.ACTIVE && !isAccountLocked() && !isDeleted();
    }

    /**
     * Checks if password reset token is still valid.
     * @return true if the token is valid
     */
    @Transient
    public boolean isPasswordResetTokenValid() {
        return passwordResetToken != null &&
               passwordResetTokenExpiresAt != null &&
               passwordResetTokenExpiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * Increments failed login attempts and locks the account if threshold is reached.
     * @param maxAttempts the maximum number of failed attempts before locking
     * @param lockDurationMinutes the duration in minutes to lock the account
     */
    public void incrementFailedAttempts(int maxAttempts, int lockDurationMinutes) {
        this.failedLoginAttempts = (this.failedLoginAttempts == null ? 0 : this.failedLoginAttempts) + 1;

        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
        }
    }

    /**
     * Resets failed login attempts and unlocks the account.
     */
    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /**
     * Records a successful login.
     * @param ipAddress the IP address of the login
     */
    public void recordSuccessfulLogin(String ipAddress) {
        this.lastLoginAt = LocalDateTime.now();
        this.lastLoginIp = ipAddress;
        resetFailedAttempts();
    }

    /**
     * Sets a new password reset token with expiration.
     * @param token the reset token
     * @param expirationMinutes how long the token is valid
     */
    public void setPasswordResetToken(String token, int expirationMinutes) {
        this.passwordResetToken = token;
        this.passwordResetTokenExpiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);
    }

    /**
     * Clears the password reset token after use.
     */
    public void clearPasswordResetToken() {
        this.passwordResetToken = null;
        this.passwordResetTokenExpiresAt = null;
    }

    /**
     * Updates the password and records the change.
     * @param newPasswordHash the new hashed password
     */
    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = LocalDateTime.now();
        clearPasswordResetToken();
    }

    /**
     * Marks the email as verified.
     */
    public void markEmailAsVerified() {
        this.emailVerified = Boolean.TRUE;
        this.emailVerificationToken = null;
        this.emailVerificationSentAt = null;
    }

    /**
     * Gets the full name of the user.
     * @return the concatenated first and last name
     */
    @Transient
    public String getFullName() {
        return String.format("%s %s", firstName, lastName).trim();
    }

    /**
     * Gets the display name (username or full name).
     * @return the display name
     */
    @Transient
    public String getDisplayName() {
        String fullName = getFullName();
        return !fullName.isEmpty() ? fullName : username;
    }

    @PrePersist
    private void prePersist() {
        if (role == null) {
            role = Role.USER;
        }
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        if (emailVerified == null) {
            emailVerified = Boolean.FALSE;
        }
        if (twoFactorEnabled == null) {
            twoFactorEnabled = Boolean.FALSE;
        }
        if (failedLoginAttempts == null) {
            failedLoginAttempts = 0;
        }
    }
}