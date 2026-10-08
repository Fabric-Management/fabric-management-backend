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
 * Deletes edit sessions that ended (closed or expired) longer than the retention ago (CEDIT-06
 * §2.2). They are technical presence rows, not history; nothing reads an ended session. Presence
 * never depends on this job: every read filters by expiry.
 *
 * <p>A scheduled method has no ambient tenant and is blind under RLS, so, as the data retention
 * policy and ADR-001 decision 9 require, it runs through {@link SystemTransactionExecutor} with an
 * explicit tenant filter: each active tenant is cleaned in its own short batches, and the tenant of
 * every deleted row is named in the statement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveEditSessionRetentionJob {

  static final String ACTIVE_TENANTS_SQL =
      "SELECT id FROM common_tenant.common_tenant WHERE is_active = true";

  static final String DELETE_ENDED_SQL =
      """
      DELETE FROM common_infrastructure.live_edit_session s
      WHERE s.id IN (
        SELECT e.id FROM common_infrastructure.live_edit_session e
        WHERE e.tenant_id = ?
          AND COALESCE(e.closed_at, e.expires_at) < ?
        ORDER BY COALESCE(e.closed_at, e.expires_at), e.id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      )
      """;

  private final SystemTransactionExecutor systemExecutor;
  private final LiveEditSessionProperties properties;
  private final Clock clock;

  @Scheduled(cron = "${application.realtime.edit-session.cleanup-cron:0 20 4 * * *}", zone = "UTC")
  public void cleanUp() {
    if (!properties.isCleanupEnabled()) {
      log.debug("LiveEditSessionRetentionJob disabled; skipping.");
      return;
    }
    int deleted = purge(clock.instant());
    if (deleted > 0) {
      log.info("LiveEditSessionRetentionJob deleted {} ended edit sessions", deleted);
    }
  }

  /** Deletes, tenant by tenant, every session that ended before the retention; the tests' entry. */
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
        log.warn("LiveEditSessionRetentionJob tenant={} failed; continuing", tenantId, failure);
      }
    }
    return total;
  }

  /** Repeats one short delete transaction for the tenant until a batch comes back short. */
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
