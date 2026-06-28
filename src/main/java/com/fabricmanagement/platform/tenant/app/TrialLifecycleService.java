package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Activation-based trial lifecycle.
 *
 * <p>Uses the system datasource because trial maintenance runs across tenants and must bypass
 * tenant self-row RLS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrialLifecycleService implements TrialLifecyclePort {

  private static final String TENANT_WRITABLE_CACHE = "tenant-writable";

  private final SystemTransactionExecutor systemExecutor;
  private final CacheManager cacheManager;

  @Value("${application.trial.base-days:90}")
  private int baseDays;

  @Value("${application.trial.dormancy-window-days:90}")
  private int dormancyWindowDays;

  @Value("${application.trial.hard-cap-months:18}")
  private int hardCapMonths;

  @Override
  public int startSelfServiceTrialIfNeeded(UUID tenantId) {
    if (tenantId == null) {
      return 0;
    }
    Instant now = Instant.now();
    Instant trialEndsAt = now.plus(baseDays, ChronoUnit.DAYS);
    return systemExecutor.executeUpdate(
        """
        UPDATE common_tenant.common_tenant
        SET trial_started_at = ?,
            last_activity_at = ?,
            trial_ends_at = ?,
            updated_at = now(),
            version = version + 1
        WHERE id = ?
          AND type = 'REGULAR'
          AND status = 'TRIAL'
          AND trial_started_at IS NULL
          AND demo_mode = false
          AND is_active = true
        """,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(trialEndsAt),
        tenantId);
  }

  @Async
  @Override
  public void touchTenantActivity(UUID tenantId) {
    if (tenantId == null) {
      return;
    }
    Instant now = Instant.now();
    Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);

    int affected =
        systemExecutor.executeUpdate(
            """
            UPDATE common_tenant.common_tenant
            SET last_activity_at = ?,
                updated_at = now(),
                version = version + 1
            WHERE id = ?
              AND type = 'REGULAR'
              AND status = 'TRIAL'
              AND trial_started_at IS NOT NULL
              AND is_active = true
              AND (last_activity_at IS NULL OR last_activity_at < ?)
            """,
            Timestamp.from(now),
            tenantId,
            Timestamp.from(todayStart));
    log.trace("Trial activity touch: tenantId={}, affected={}", tenantId, affected);
  }

  @Scheduled(cron = "${application.trial.lifecycle-cron:0 15 3 * * ?}")
  public void refreshTrialWindows() {
    Instant now = Instant.now();
    int updated =
        systemExecutor.executeInTransaction(
            jdbc -> {
              var rows =
                  jdbc.query(
                      """
                      SELECT id, trial_started_at, last_activity_at
                      FROM common_tenant.common_tenant
                      WHERE type = 'REGULAR'
                        AND status = 'TRIAL'
                        AND trial_started_at IS NOT NULL
                        AND is_active = true
                      """,
                      (rs, rowNum) ->
                          new TrialWindow(
                              rs.getObject("id", UUID.class),
                              rs.getTimestamp("trial_started_at").toInstant(),
                              rs.getTimestamp("last_activity_at") != null
                                  ? rs.getTimestamp("last_activity_at").toInstant()
                                  : null));
              int count = 0;
              for (TrialWindow row : rows) {
                TrialDecision decision = evaluate(row, now);
                count +=
                    jdbc.update(
                        """
                        UPDATE common_tenant.common_tenant
                        SET status = ?,
                            trial_ends_at = ?,
                            updated_at = now(),
                            version = version + 1
                        WHERE id = ?
                        """,
                        decision.status().name(),
                        Timestamp.from(decision.effectiveExpiry()),
                        row.tenantId());
                if (decision.status() == TenantStatus.EXPIRED) {
                  evictWritableCache(row.tenantId());
                }
              }
              return count;
            });
    log.info("Trial lifecycle refresh completed: {} tenant(s) updated", updated);
  }

  TrialDecision evaluate(TrialWindow row, Instant now) {
    Instant lastActivity =
        row.lastActivityAt() != null ? row.lastActivityAt() : row.trialStartedAt();
    Instant dormancyExpiry = lastActivity.plus(dormancyWindowDays, ChronoUnit.DAYS);
    Instant hardCapAt =
        row.trialStartedAt().atOffset(ZoneOffset.UTC).plusMonths(hardCapMonths).toInstant();
    Instant effectiveExpiry = dormancyExpiry.isBefore(hardCapAt) ? dormancyExpiry : hardCapAt;
    TenantStatus status = now.isAfter(effectiveExpiry) ? TenantStatus.EXPIRED : TenantStatus.TRIAL;
    return new TrialDecision(status, effectiveExpiry);
  }

  private void evictWritableCache(UUID tenantId) {
    var cache = cacheManager.getCache(TENANT_WRITABLE_CACHE);
    if (cache != null) {
      cache.evict(tenantId.toString());
    }
  }

  record TrialWindow(UUID tenantId, Instant trialStartedAt, Instant lastActivityAt) {}

  record TrialDecision(TenantStatus status, Instant effectiveExpiry) {}
}
