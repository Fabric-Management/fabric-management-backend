package com.fabricmanagement.platform.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A self-service signup whose e-mail address has not been proved yet (SIGNUP-DEFER-1).
 *
 * <p>This is all a public signup writes: no tenant, no users, no demo data. Using the e-mailed link
 * builds the tenant. Like {@link LoginIdentity} the entity deliberately does not extend BaseEntity:
 * it has no tenant_id, is not tenant scoped, and is read before any tenant exists.
 *
 * <p>Only the SHA-256 hash of the setup token is stored; the token itself is only in the mail. That
 * is why a repeated signup issues a new link instead of re-sending the old one.
 *
 * <p>Rows are inserted by {@code PendingSignupRepository#insertIfAbsent} (so a concurrent signup
 * loses cleanly on the unique indexes) and replaced by {@code replaceIfUnderLimit}, one atomic
 * statement that also counts the links issued. This entity is for reading.
 */
@Entity
@Table(name = "pending_signup", schema = "common_auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingSignup {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  /** Lower-case and trimmed, the same rule as the login identity. */
  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "tax_id", nullable = false, length = 50)
  private String taxId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request", nullable = false, columnDefinition = "jsonb")
  private PendingSignupRequest request;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "terms_accepted_at", nullable = false)
  private Instant termsAcceptedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** Links mailed for this e-mail address while the signup has been open. */
  @Column(name = "send_count", nullable = false)
  private int sendCount;

  @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_tenant_id")
  private UUID completedTenantId;
}
