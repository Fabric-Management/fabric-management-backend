package com.fabricmanagement.flowboard.dashboard.app.scheduler;

import com.fabricmanagement.common.platform.tenant.app.TenantService;
import com.fabricmanagement.common.platform.tenant.dto.TenantDto;
import com.fabricmanagement.flowboard.dashboard.domain.BadgeType;
import com.fabricmanagement.flowboard.dashboard.domain.UserPerformanceSnapshot;
import com.fabricmanagement.flowboard.dashboard.infra.repository.UserPerformanceSnapshotRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
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

/**
 * Haftalık performans snapshot'ı alır ve liderlik tablosu puanları + rozetleri hesaplar.
 *
 * <p>[K2 FIX] Dummy data kaldırıldı. Gerçek tenant/user iterasyonu ve TaskRepository sorguları
 * eklendi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowBoardPerformanceJob {

  private final UserPerformanceSnapshotRepository snapshotRepo;
  private final TaskRepository taskRepo;
  private final Clock clock;
  private final TenantService tenantService;

  private static final int POINTS_PER_COMPLETED = 10;
  private static final int PENALTY_PER_OVERDUE = 5;
  private static final int SPEEDSTER_THRESHOLD = 100;

  /** Haftalık snapshot alır. (Pazar gece yarısı) */
  @Scheduled(cron = "0 0 0 * * SUN")
  public void runWeeklyPerformanceSnapshot() {
    log.info("Starting FlowBoardPerformanceJob...");
    LocalDate snapshotDate = LocalDate.now(clock).minus(1, ChronoUnit.WEEKS);
    Instant weekStart = snapshotDate.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant weekEnd = weekStart.plus(7, ChronoUnit.DAYS);

    int saved = 0;
    int failed = 0;

    try {
      List<TenantDto> activeTenants = tenantService.getAllActive();

      for (TenantDto tenant : activeTenants) {
        UUID tenantId = tenant.getId();
        log.debug("Processing performance snapshot for tenant: {}", tenantId);

        try {
          List<UUID> allUserIds = taskRepo.findDistinctAssigneeUserIds(tenantId);

          for (UUID userId : allUserIds) {
            try {
              int completed =
                  taskRepo.countCompletedTasksInPeriod(tenantId, userId, weekStart, weekEnd);
              int overdue =
                  taskRepo.countOverdueCompletedTasksInPeriod(tenantId, userId, weekStart, weekEnd);

              int points = (completed * POINTS_PER_COMPLETED) - (overdue * PENALTY_PER_OVERDUE);
              List<String> badges = new ArrayList<>();
              BadgeType topBadge = null;

              // [D6 FIX] SPEEDSTER dışındaki diğer rozetler de işleme alındı
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

              String badgesJson =
                  badges.isEmpty() ? "[]" : "[\"" + String.join("\",\"", badges) + "\"]";

              UserPerformanceSnapshot snapshot =
                  new UserPerformanceSnapshot(
                      tenantId, // cross-tenant batch yerine gerçek tenantId kullanıldı
                      userId,
                      snapshotDate,
                      completed,
                      overdue,
                      BigDecimal.ZERO, // estimatedHours — ilerde sumEstimated ile doldurulacak
                      BigDecimal.ZERO, // actualHours — ilerde TimeEntry toplamı ile doldurulacak
                      points,
                      badgesJson,
                      topBadge);

              // Idempotency: aynı (tenant, user, hafta) için tekrar çalışmada duplicate oluşmasın
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
              snapshotRepo.save(snapshot);
              saved++;
            } catch (Exception e) {
              failed++;
              log.error(
                  "FlowBoardPerformanceJob: Failed for userId={}: {}", userId, e.getMessage());
            }
          }
        } catch (Exception e) {
          log.error(
              "FlowBoardPerformanceJob: Failed to process tenantId={}: {}",
              tenantId,
              e.getMessage());
        }
      }
    } catch (Exception e) {
      log.error("FlowBoardPerformanceJob: Critical failure: {}", e.getMessage(), e);
    }

    log.info("FlowBoardPerformanceJob completed: saved={}, failed={}", saved, failed);
  }
}
