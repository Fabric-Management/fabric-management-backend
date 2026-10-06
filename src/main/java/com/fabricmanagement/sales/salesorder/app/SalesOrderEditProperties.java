package com.fabricmanagement.sales.salesorder.app;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Technical defaults of the safe edit (CEDIT-03 §6). They bound server bases, save receipts and
 * request size; they are not business-document retention, and the field history has no expiry.
 * Expiry is checked on every save, so correctness never depends on the cleanup running on time.
 */
@Component
@ConfigurationProperties(prefix = "sales.edit")
@Getter
@Setter
public class SalesOrderEditProperties implements InitializingBean {

  /** How long a created base is valid; fixed at creation, never extended by use. */
  private Duration baseTtl = Duration.ofHours(8);

  /** How long an expired base is kept for review before it may be cleaned up. */
  private Duration expiredBaseRetention = Duration.ofDays(7);

  /** The least time a receipt is kept after it was recorded; a repeat is answered within it. */
  private Duration operationRetention = Duration.ofDays(30);

  /** Line operations (ADD + UPDATE + REMOVE) one save may carry; also published as maxItems. */
  private int maxLineOperations = 200;

  /** Whether the scheduled cleanup runs; expiry is enforced on save either way. */
  private boolean cleanupEnabled = true;

  /** When the cleanup runs, in UTC. */
  private String cleanupCron = "0 45 3 * * *";

  /** Rows deleted per tenant in one short cleanup transaction. */
  private int cleanupBatchSize = 500;

  @Override
  public void afterPropertiesSet() {
    requirePositive("sales.edit.base-ttl", baseTtl);
    requirePositive("sales.edit.expired-base-retention", expiredBaseRetention);
    requirePositive("sales.edit.operation-retention", operationRetention);
    if (maxLineOperations < 1) {
      throw new IllegalStateException("sales.edit.max-line-operations must be positive");
    }
    if (cleanupBatchSize < 1) {
      throw new IllegalStateException("sales.edit.cleanup-batch-size must be positive");
    }
    if (cleanupCron == null || cleanupCron.isBlank()) {
      throw new IllegalStateException("sales.edit.cleanup-cron must be set");
    }
    // A receipt must outlive the bases a repeat may still name; otherwise a repeat after the
    // receipt was cleaned would be judged as a new save against a base that is still there.
    if (operationRetention.compareTo(baseTtl.plus(expiredBaseRetention)) < 0) {
      throw new IllegalStateException(
          "sales.edit.operation-retention must be at least base-ttl + expired-base-retention");
    }
  }

  private static void requirePositive(String name, Duration value) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalStateException(name + " must be a positive duration");
    }
  }
}
