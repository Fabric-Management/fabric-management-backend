package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deletes field-lease rows whose last period ended (released or expired) longer than the retention
 * ago (CEDIT-07 §5). They are technical ownership rows, not history: a saved change keeps its own
 * field history, which this job never touches. Nothing about safety depends on this job: every
 * decision filters by release, expiry and generation, and a row created again after cleanup starts
 * a new period with a new token, so an old token never matches it (no ABA).
 *
 * <p>A row of an older resource generation that is still unexpired is left for a later run; it is
 * void already. Same pattern as {@link LiveEditSessionRetentionJob}: the system role through {@link
 * SystemTransactionExecutor}, each active tenant in its own short batches, the tenant named in the
 * statement, rows another transaction holds skipped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveEditLeaseRetentionJob {

  static final String ACTIVE_TENANTS_SQL =
      "SELECT id FROM common_tenant.common_tenant WHERE is_active = true";

  static final String DELETE_ENDED_SQL =
      """
      DELETE FROM common_infrastructure.live_edit_lease l
      WHERE l.id IN (
        SELECT e.id FROM common_infrastructure.live_edit_lease e
        WHERE e.tenant_id = ?
          AND LEAST(COALESCE(e.released_at, e.expires_at), e.expires_at) < ?
        ORDER BY e.expires_at, e.id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      )
      """;

  private final SystemTransactionExecutor systemExecutor;
  private final LiveEditLeaseProperties properties;
  private final Clock clock;

  @Scheduled(cron = "${application.realtime.edit-lease.cleanup-cron:0 25 4 * * *}", zone = "UTC")
  public void cleanUp() {
    if (!properties.isCleanupEnabled()) {
      log.debug("LiveEditLeaseRetentionJob disabled; skipping.");
      return;
    }
    int deleted = purge(clock.instant());
    if (deleted > 0) {
      log.info("LiveEditLeaseRetentionJob deleted {} ended field-lease rows", deleted);
    }
  }

  /** Deletes, tenant by tenant, every row that ended before the retention; the tests' entry. */
  public int purge(Instant now) {
    Timestamp threshold = Timestamp.from(now.minus(properties.getRetention()));
    List<UUID> tenantIds =
        systemExecutor.executeQuery(
            ACTIVE_TENANTS_SQL, (rs, rowNum) -> UUID.fromString(rs.getString("id")));
    int total = 0;
    for (UUID tenantId : tenantIds) {
      try {
        total += purgeTenant(tenantId, threshold);
      } catch (RuntimeException failure) {
        // One tenant's failure must not stop the others; its rows wait for the next run.
        log.warn("LiveEditLeaseRetentionJob tenant={} failed; continuing", tenantId, failure);
      }
    }
    return total;
  }

  private int purgeTenant(UUID tenantId, Timestamp threshold) {
    int batch = properties.getCleanupBatchSize();
    int total = 0;
    int deleted;
    do {
      deleted =
          systemExecutor.executeInTransaction(
              jdbc -> jdbc.update(DELETE_ENDED_SQL, tenantId, threshold, batch));
      total += deleted;
    } while (deleted >= batch);
    return total;
  }
}
