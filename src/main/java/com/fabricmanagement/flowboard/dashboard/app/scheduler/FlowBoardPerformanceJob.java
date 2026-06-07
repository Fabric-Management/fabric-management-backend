package com.fabricmanagement.flowboard.dashboard.app.scheduler;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.dashboard.domain.BadgeType;
import com.fabricmanagement.flowboard.dashboard.domain.UserPerformanceSnapshot;
import com.fabricmanagement.flowboard.dashboard.infra.repository.UserPerformanceSnapshotRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowBoardPerformanceJob {

  private final UserPerformanceSnapshotRepository snapshotRepo;
  private final TaskRepository taskRepo;
  private final Clock clock;
  private final TenantSystemService tenantService;
  private final ObjectMapper objectMapper;

  private static final int POINTS_PER_COMPLETED = 10;
  private static final int PENALTY_PER_OVERDUE = 5;
  private static final int SPEEDSTER_THRESHOLD = 100;

  @Scheduled(cron = "0 0 0 * * SUN")
  public void runWeeklyPerformanceSnapshot() {
    log.info("Starting FlowBoardPerformanceJob...");
    LocalDate snapshotDate = LocalDate.now(clock).minus(1, ChronoUnit.WEEKS);
    Instant weekStart = snapshotDate.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant weekEnd = weekStart.plus(7, ChronoUnit.DAYS);

    List<TenantDto> activeTenants;
    try {
      activeTenants = tenantService.getAllActive();
    } catch (Exception e) {
      log.error("FlowBoardPerformanceJob: Failed to load tenants: {}", e.getMessage(), e);
      return;
    }

    int totalSaved = 0;
    int totalFailed = 0;

    for (TenantDto tenant : activeTenants) {
      try {
        int[] counts =
            TenantContext.executeInTenantContext(
                tenant.getId(),
                () -> processSnapshotsForTenant(tenant.getId(), snapshotDate, weekStart, weekEnd));
        totalSaved += counts[0];
        totalFailed += counts[1];
      } catch (Exception e) {
        log.error(
            "FlowBoardPerformanceJob: Failed for tenant={}: {}", tenant.getId(), e.getMessage());
      }
    }

    log.info("FlowBoardPerformanceJob completed: saved={}, failed={}", totalSaved, totalFailed);
  }

  private int[] processSnapshotsForTenant(
      UUID tenantId, LocalDate snapshotDate, Instant weekStart, Instant weekEnd) {
    int saved = 0;
    int failed = 0;

    List<UUID> allUserIds = taskRepo.findDistinctAssigneeUserIds(tenantId);

    for (UUID userId : allUserIds) {
      try {
        boolean alreadyExists =
            snapshotRepo
                .findByTenantIdAndUserIdAndSnapshotDateAndDeletedAtIsNull(
                    tenantId, userId, snapshotDate)
                .isPresent();
        if (alreadyExists) {
          log.debug(
              "Snapshot already exists, skipping: tenantId={}, userId={}, date={}",
              tenantId,
              userId,
              snapshotDate);
          continue;
        }

        int completed = taskRepo.countCompletedTasksInPeriod(tenantId, userId, weekStart, weekEnd);
        int overdue =
            taskRepo.countOverdueCompletedTasksInPeriod(tenantId, userId, weekStart, weekEnd);
        int points = (completed * POINTS_PER_COMPLETED) - (overdue * PENALTY_PER_OVERDUE);

        List<String> badges = new ArrayList<>();
        BadgeType topBadge = null;

        if (points > SPEEDSTER_THRESHOLD) {
          badges.add("SPEEDSTER");
          topBadge = BadgeType.SPEEDSTER;
        }
        if (completed >= 5 && overdue == 0) {
          badges.add("QUALITY_CHAMPION");
          if (topBadge == null) topBadge = BadgeType.QUALITY_CHAMPION;
        }
        if (completed >= 10 && overdue <= 1) {
          badges.add("CONSISTENT_PERFORMER");
          if (topBadge == null) topBadge = BadgeType.CONSISTENT_PERFORMER;
        }

        String badgesJson;
        try {
          badgesJson = objectMapper.writeValueAsString(badges);
        } catch (Exception e) {
          badgesJson = "[]";
        }

        UserPerformanceSnapshot snapshot =
            new UserPerformanceSnapshot(
                tenantId,
                userId,
                snapshotDate,
                completed,
                overdue,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                points,
                badgesJson,
                topBadge);

        snapshotRepo.save(snapshot);
        saved++;
      } catch (Exception e) {
        failed++;
        log.error("FlowBoardPerformanceJob: Failed for userId={}: {}", userId, e.getMessage());
      }
    }
    return new int[] {saved, failed};
  }
}
