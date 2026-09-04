package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class YarnBackfillConcurrencyIT extends YarnBackfillIntegrationSupport {

  @Test
  void heldTransactionLockReturnsImmediatelyWithoutScanThenReleasedRunCompletes() throws Exception {
    TenantFixture fixture = insertTenant("lock", 1);
    CountDownLatch acquired = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> holder =
        CompletableFuture.runAsync(
            () ->
                systemTransactions.executeInTransaction(
                    jdbc -> {
                      jdbc.queryForObject(
                          "SELECT pg_advisory_xact_lock("
                              + "hashtext('yarn-backfill:' || CAST(? AS text)))",
                          (result, row) -> Boolean.TRUE,
                          fixture.tenantId());
                      acquired.countDown();
                      try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                          throw new IllegalStateException("Timed out waiting to release test lock");
                        }
                      } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                      }
                      return null;
                    }));
    assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();

    YarnLegacyBackfillReport skipped =
        assertTimeout(Duration.ofSeconds(1), () -> backfill(fixture));
    assertThat(skipped.outcome()).isEqualTo(YarnLegacyBackfillOutcome.LOCK_SKIPPED);
    assertThat(skipped.productsScanned()).isZero();
    assertThat(articleCount(fixture)).isZero();

    release.countDown();
    holder.get(5, TimeUnit.SECONDS);
    YarnLegacyBackfillReport completed = backfill(fixture);
    assertThat(completed.outcome()).isEqualTo(YarnLegacyBackfillOutcome.COMPLETED);
    assertThat(completed.articlesCreated()).isEqualTo(1);
  }

  @Test
  void concurrentCallsLeaveOneArticleSetAndNoDuplicateQueueRows() throws Exception {
    TenantFixture fixture = insertTenant("concurrent", 1);
    insertBatch(fixture, 0, "Ne 20/1", java.time.Instant.parse("2026-08-31T12:00:00Z"));
    insertBatch(fixture, 0, "Ne 40/1", java.time.Instant.parse("2026-08-31T11:59:00Z"));
    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<YarnLegacyBackfillReport> first = concurrentBackfill(fixture, start);
    CompletableFuture<YarnLegacyBackfillReport> second = concurrentBackfill(fixture, start);

    start.countDown();
    List<YarnLegacyBackfillReport> reports =
        List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

    assertThat(reports)
        .anySatisfy(
            report -> assertThat(report.outcome()).isEqualTo(YarnLegacyBackfillOutcome.COMPLETED));
    assertThat(articleCount(fixture)).isEqualTo(1L);
    assertThat(queueCount(fixture)).isEqualTo(1L);
  }

  private CompletableFuture<YarnLegacyBackfillReport> concurrentBackfill(
      TenantFixture fixture, CountDownLatch start) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            start.await();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
          return backfill(fixture);
        });
  }

  private long articleCount(TenantFixture fixture) {
    return queryOne(
        "SELECT count(*) FROM production.prod_yarn_article WHERE tenant_id=?",
        Long.class,
        fixture.tenantId());
  }

  private long queueCount(TenantFixture fixture) {
    return queryOne(
        "SELECT count(*) FROM production.prod_yarn_backfill_reconciliation WHERE tenant_id=?",
        Long.class,
        fixture.tenantId());
  }
}
