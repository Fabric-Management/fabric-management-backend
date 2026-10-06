package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cleans up safe-edit technical rows (CEDIT-03 §6): expired edit bases after their review window
 * (never the parent of a base that is still valid), then receipts past their retention that no kept
 * base still names. The order and its field history are never touched. Correctness never depends on
 * this job: expiry is checked on every save, and a receipt or base in use by an open save is
 * share-locked and skipped here.
 *
 * <p>{@link Scheduled} methods have no ambient tenant and are blind under RLS, so each active
 * tenant is cleaned in its own short transactions through {@link SystemTransactionExecutor}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalesOrderEditRetentionJob {

  static final String ACTIVE_TENANTS_SQL =
      "SELECT id FROM common_tenant.common_tenant WHERE is_active = true";

  static final String DELETE_EXPIRED_BASES_SQL =
      """
      DELETE FROM sales_ord.order_edit_base b
      WHERE b.id IN (
        SELECT c.id FROM sales_ord.order_edit_base c
        WHERE c.tenant_id = ?
          AND c.expires_at < ?
          AND NOT EXISTS (
            SELECT 1 FROM sales_ord.order_edit_base child
            WHERE child.tenant_id = c.tenant_id
              AND child.parent_base_id = c.id
              AND child.expires_at > ?
          )
        ORDER BY c.expires_at, c.id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      )
      """;

  static final String DELETE_OLD_RECEIPTS_SQL =
      """
      DELETE FROM sales_ord.order_edit_operation o
      WHERE o.id IN (
        SELECT r.id FROM sales_ord.order_edit_operation r
        WHERE r.tenant_id = ?
          AND r.recorded_at < ?
          AND NOT EXISTS (
            SELECT 1 FROM sales_ord.order_edit_base b
            WHERE b.tenant_id = r.tenant_id
              AND b.origin_operation_id = r.operation_id
          )
        ORDER BY r.recorded_at, r.id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      )
      """;

  private final SystemTransactionExecutor systemExecutor;
  private final SalesOrderEditProperties properties;
  private final Clock clock;

  @Scheduled(cron = "${sales.edit.cleanup-cron:0 45 3 * * *}", zone = "UTC")
  public void cleanUp() {
    if (!properties.isCleanupEnabled()) {
      log.debug("SalesOrderEditRetentionJob disabled; skipping.");
      return;
    }
    Totals totals = purgeAll(clock.instant());
    if (totals.bases() > 0 || totals.receipts() > 0) {
      log.info(
          "SalesOrderEditRetentionJob completed: tenants={}, bases={}, receipts={}",
          totals.tenants(),
          totals.bases(),
          totals.receipts());
    }
  }

  /** Cleans every active tenant as of {@code now}; also the entry point of the tests. */
  public Totals purgeAll(Instant now) {
    List<UUID> tenantIds =
        systemExecutor.executeQuery(
            ACTIVE_TENANTS_SQL, (rs, rowNum) -> UUID.fromString(rs.getString("id")));
    int bases = 0;
    int receipts = 0;
    for (UUID tenantId : tenantIds) {
      Totals tenant;
      try {
        tenant = TenantContext.executeInTenantContext(tenantId, () -> purgeTenant(tenantId, now));
      } catch (RuntimeException failure) {
        // One tenant's failure must not stop the others; its rows wait for the next run.
        log.warn("SalesOrderEditRetentionJob tenant={} failed; continuing", tenantId, failure);
        continue;
      }
      bases += tenant.bases();
      receipts += tenant.receipts();
      if (tenant.bases() > 0 || tenant.receipts() > 0) {
        log.info(
            "SalesOrderEditRetentionJob tenant={} deleted bases={}, receipts={}",
            tenantId,
            tenant.bases(),
            tenant.receipts());
      }
    }
    return new Totals(tenantIds.size(), bases, receipts);
  }

  Totals purgeTenant(UUID tenantId, Instant now) {
    Timestamp baseThreshold = Timestamp.from(now.minus(properties.getExpiredBaseRetention()));
    Timestamp receiptThreshold = Timestamp.from(now.minus(properties.getOperationRetention()));
    int batch = properties.getCleanupBatchSize();
    // Bases first: a receipt is kept while a base still names it as its origin.
    int bases =
        inBatches(
            batch,
            () ->
                systemExecutor.executeInTransaction(
                    jdbc ->
                        jdbc.update(
                            DELETE_EXPIRED_BASES_SQL,
                            tenantId,
                            baseThreshold,
                            Timestamp.from(now),
                            batch)));
    int receipts =
        inBatches(
            batch,
            () ->
                systemExecutor.executeInTransaction(
                    jdbc ->
                        jdbc.update(DELETE_OLD_RECEIPTS_SQL, tenantId, receiptThreshold, batch)));
    return new Totals(1, bases, receipts);
  }

  /** Repeats one short delete transaction until a batch comes back short. */
  private static int inBatches(int batch, IntSupplier deleteBatch) {
    int total = 0;
    int deleted;
    do {
      deleted = deleteBatch.getAsInt();
      total += deleted;
    } while (deleted >= batch);
    return total;
  }

  /** What one run deleted. */
  public record Totals(int tenants, int bases, int receipts) {}
}
