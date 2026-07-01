package com.fabricmanagement.platform.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Platform-level authentication principal.
 *
 * <p>This entity deliberately does not extend BaseEntity: it has no tenant_id, is not tenant
 * scoped, and must remain usable before a tenant context exists.
 */
@Entity
@Table(
    name = "login_identity",
    schema = "common_auth",
    indexes = {@Index(name = "idx_login_identity_email", columnList = "email")},
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_login_identity_email",
          columnNames = {"email"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginIdentity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "is_mfa_enabled", nullable = false)
  @Builder.Default
  private Boolean isMfaEnabled = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "primary_mfa_type", nullable = false, length = 30)
  @Builder.Default
  private MfaType primaryMfaType = MfaType.NONE;

  @Column(name = "mfa_secret", length = 64)
  private String mfaSecret;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  @Column(name = "email_verified", nullable = false)
  @Builder.Default
  private Boolean emailVerified = false;

  @Column(name = "failed_login_attempts", nullable = false)
  @Builder.Default
  private Integer failedLoginAttempts = 0;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "requires_password_reset", nullable = false)
  @Builder.Default
  private Boolean requiresPasswordReset = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  @PreUpdate
  void normalizeEmail() {
    if (email != null) {
      email = email.trim().toLowerCase(java.util.Locale.ROOT);
    }
  }
}
