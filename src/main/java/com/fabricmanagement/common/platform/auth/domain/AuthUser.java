package com.fabricmanagement.common.platform.auth.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * AuthUser entity - Authentication credentials for users.
 *
 * <p><b>CRITICAL DESIGN CHANGE:</b>
 *
 * <ul>
 *   <li>✅ User-based authentication (one AuthUser per User)
 *   <li>✅ Multi-contact login supported (any verified contact can login)
 *   <li>✅ Password stored as BCrypt hash
 *   <li>✅ Verification status tracked
 *   <li>✅ Failed login attempts & lockout
 *   <li>✅ Last login tracking
 * </ul>
 *
 * <h2>Design Benefits:</h2>
 *
 * <ul>
 *   <li>Single password record per user (no duplication)
 *   <li>Simpler password management
 *   <li>Better performance (less data, faster queries)
 *   <li>Easier tenant isolation
 * </ul>
 *
 * <h2>Security Features:</h2>
 *
 * <ul>
 *   <li>BCrypt password hashing (strength 10)
 *   <li>Account lockout after 5 failed attempts (30 min)
 *   <li>Must be verified before login
 *   <li>Tracks last login for security audit
 * </ul>
 */
@Entity
@Table(
    name = "common_auth_user",
    schema = "common_auth",
    indexes = {@Index(name = "idx_auth_user", columnList = "user_id", unique = true)})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser extends BaseEntity {

  /**
   * User entity reference (new user-based authentication).
   *
   * <p>One AuthUser per User. Any verified contact of this User can be used for login.
   */
  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private com.fabricmanagement.common.platform.user.domain.User user;

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
