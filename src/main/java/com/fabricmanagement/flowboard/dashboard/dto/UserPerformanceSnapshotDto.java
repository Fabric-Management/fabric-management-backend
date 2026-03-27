package com.fabricmanagement.flowboard.dashboard.dto;

import com.fabricmanagement.flowboard.dashboard.domain.BadgeType;
import com.fabricmanagement.flowboard.dashboard.domain.UserPerformanceSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UserPerformanceSnapshotDto(
    UUID id,
    UUID userId,
    LocalDate snapshotDate,
    int completedTasks,
    int overdueTasks,
    BigDecimal totalEstimatedHours,
    BigDecimal totalActualHours,
    int totalPoints,
    String earnedBadges,
    BadgeType topBadge) {

  public static UserPerformanceSnapshotDto from(UserPerformanceSnapshot s) {
    return new UserPerformanceSnapshotDto(
        s.getId(),
        s.getUserId(),
        s.getSnapshotDate(),
        s.getCompletedTasks(),
        s.getOverdueTasks(),
        s.getTotalEstimatedHours(),
        s.getTotalActualHours(),
        s.getTotalPoints(),
        s.getEarnedBadges(),
        s.getTopBadge());
  }
}
