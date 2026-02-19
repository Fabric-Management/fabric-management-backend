package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.organization.domain.Position;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

/**
 * User-Position junction entity (Many-to-Many relationship).
 *
 * <p>Represents the assignment of users to positions. A user can have multiple positions, and each
 * position can be assigned to multiple users.
 *
 * <h2>Features:</h2>
 *
 * <ul>
 *   <li>Many-to-Many relationship between User and Position
 *   <li>Primary position flag (isPrimary) - one position can be marked as primary
 *   <li>Tenant isolation via tenant_id
 *   <li>Audit trail - tracks when and who assigned the relationship
 *   <li>Assignment history via effective_date/end_date
 * </ul>
 */
@Entity
@Table(
    name = "common_user_position",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_user_pos_user", columnList = "user_id"),
      @Index(name = "idx_user_pos_position", columnList = "position_id"),
      @Index(name = "idx_user_pos_primary", columnList = "user_id,is_primary"),
      @Index(name = "idx_user_pos_tenant", columnList = "tenant_id"),
      @Index(name = "idx_user_pos_tenant_user", columnList = "tenant_id,user_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserPositionId.class)
public class UserPosition {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "position_id", nullable = false)
  private UUID positionId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "position_id", insertable = false, updatable = false)
  private Position position;

  @Column(name = "is_primary", nullable = false)
  @Builder.Default
  private Boolean isPrimary = false;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  @Column(name = "assigned_at", nullable = false)
  @Builder.Default
  private Instant assignedAt = Instant.now();

  @Column(name = "assigned_by")
  private UUID assignedBy;

  @Column(name = "effective_date", nullable = false)
  @Builder.Default
  private LocalDate effectiveDate = LocalDate.now();

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  public static UserPosition create(
      User user, Position position, boolean isPrimary, UUID assignedBy) {
    return UserPosition.builder()
        .userId(user.getId())
        .positionId(position.getId())
        .tenantId(TenantContext.getCurrentTenantId())
        .user(user)
        .position(position)
        .isPrimary(isPrimary)
        .assignedBy(assignedBy)
        .assignedAt(Instant.now())
        .effectiveDate(LocalDate.now())
        .build();
  }

  public void endAssignment(LocalDate endDate) {
    this.endDate = endDate;
    this.isActive = false;
  }

  public void reactivate() {
    this.endDate = null;
    this.isActive = true;
  }

  public boolean isActiveAssignment() {
    return this.endDate == null && Boolean.TRUE.equals(this.isActive);
  }

  public void markAsPrimary() {
    this.isPrimary = true;
  }

  public void markAsSecondary() {
    this.isPrimary = false;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
