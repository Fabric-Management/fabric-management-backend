package com.fabricmanagement.flowboard.dashboard.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Haftalık veya aylık olarak alınan performans anlık görüntüleri (Gamification). */
@Entity
@Table(schema = "flowboard", name = "user_performance_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPerformanceSnapshot extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "snapshot_date", nullable = false)
  private LocalDate snapshotDate;

  @Column(name = "completed_tasks", nullable = false)
  private int completedTasks = 0;

  @Column(name = "overdue_tasks", nullable = false)
  private int overdueTasks = 0;

  @Column(name = "total_estimated_hours", nullable = false)
  private BigDecimal totalEstimatedHours = BigDecimal.ZERO;

  @Column(name = "total_actual_hours", nullable = false)
  private BigDecimal totalActualHours = BigDecimal.ZERO;

  @Column(name = "total_points", nullable = false)
  private int totalPoints = 0;

  @Column(name = "earned_badges", columnDefinition = "jsonb")
  private String earnedBadges; // Mesela ["SPEEDSTER", "TEAM_PLAYER"]

  @Enumerated(EnumType.STRING)
  @Column(name = "top_badge")
  private BadgeType topBadge;

  public UserPerformanceSnapshot(
      UUID tenantId,
      UUID userId,
      LocalDate snapshotDate,
      int completedTasks,
      int overdueTasks,
      BigDecimal totalEstimatedHours,
      BigDecimal totalActualHours,
      int totalPoints,
      String earnedBadges,
      BadgeType topBadge) {
    this.setTenantId(tenantId);
    this.userId = userId;
    this.snapshotDate = snapshotDate;
    this.completedTasks = completedTasks;
    this.overdueTasks = overdueTasks;
    this.totalEstimatedHours = totalEstimatedHours;
    this.totalActualHours = totalActualHours;
    this.totalPoints = totalPoints;
    this.earnedBadges = earnedBadges;
    this.topBadge = topBadge;
  }

  @Override
  protected String getModuleCode() {
    return "PERF";
  }
}
