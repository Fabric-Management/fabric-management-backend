package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deletes edit sessions that ended (closed or expired) longer than the retention ago (CEDIT-06
 * §2.2). They are technical presence rows, not history; nothing reads an ended session. Presence
 * never depends on this job: every read filters by expiry.
 *
 * <p>A scheduled method has no ambient tenant, so the delete runs through {@link
 * SystemTransactionExecutor} (the system role, across tenants) in short batches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveEditSessionRetentionJob {

  static final String DELETE_ENDED_SQL =
      """
      DELETE FROM common_infrastructure.live_edit_session s
      WHERE s.id IN (
        SELECT e.id FROM common_infrastructure.live_edit_session e
        WHERE COALESCE(e.closed_at, e.expires_at) < ?
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

  /** Deletes every session that ended before {@code now} minus the retention; the tests' entry. */
  public int purge(Instant now) {
    Timestamp threshold = Timestamp.from(now.minus(properties.getRetention()));
    int batch = properties.getCleanupBatchSize();
    int total = 0;
    int deleted;
    do {
      deleted =
          systemExecutor.executeInTransaction(
              jdbc -> jdbc.update(DELETE_ENDED_SQL, threshold, batch));
      total += deleted;
    } while (deleted >= batch);
    return total;
  }
}
