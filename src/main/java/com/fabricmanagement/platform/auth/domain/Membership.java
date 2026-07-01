package com.fabricmanagement.platform.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Platform-level link from one login identity to one tenant user.
 *
 * <p>This entity deliberately does not extend BaseEntity. Its tenant_id is a plain FK used for
 * membership selection during pre-auth login, not an RLS isolation column.
 */
@Entity
@Table(
    name = "membership",
    schema = "common_auth",
    indexes = {
      @Index(name = "idx_membership_login_identity", columnList = "login_identity_id"),
      @Index(name = "idx_membership_tenant", columnList = "tenant_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_membership_identity_tenant",
          columnNames = {"login_identity_id", "tenant_id"}),
      @UniqueConstraint(
          name = "uk_membership_user",
          columnNames = {"user_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Membership {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "login_identity_id", nullable = false)
  private UUID loginIdentityId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "login_identity_id", insertable = false, updatable = false)
  private LoginIdentity loginIdentity;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private MembershipStatus status = MembershipStatus.ACTIVE;

  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private Boolean isDefault = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
