package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.platform.company.domain.Position;
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
 *   <li>Audit trail - tracks when and who assigned the relationship
 * </ul>
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * UserPosition assignment = UserPosition.builder()
 *     .user(user)
 *     .position(position)
 *     .isPrimary(true)
 *     .assignedBy(adminUserId)
 *     .build();
 * }</pre>
 *
 * <h2>Assignment History:</h2>
 *
 * <p>Position assignments are tracked with effective_date and end_date fields. This enables:
 *
 * <ul>
 *   <li>Job change history tracking
 *   <li>Historical reporting and analysis
 *   <li>Career progression analysis
 *   <li>Active vs. past assignments (end_date is NULL for active assignments)
 * </ul>
 */
@Entity
@Table(
    name = "common_user_position",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_user_pos_user", columnList = "user_id"),
      @Index(name = "idx_user_pos_position", columnList = "position_id"),
      @Index(name = "idx_user_pos_primary", columnList = "user_id,is_primary")
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "position_id", insertable = false, updatable = false)
  private Position position;

  @Column(name = "is_primary", nullable = false)
  @Builder.Default
  private Boolean isPrimary = false;

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

  public static UserPosition create(
      User user, Position position, boolean isPrimary, UUID assignedBy) {
    return UserPosition.builder()
        .userId(user.getId())
        .positionId(position.getId())
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
  }

  public void reactivate() {
    this.endDate = null;
  }

  public boolean isActive() {
    return this.endDate == null;
  }

  public void markAsPrimary() {
    this.isPrimary = true;
  }

  public void markAsSecondary() {
    this.isPrimary = false;
  }
}
