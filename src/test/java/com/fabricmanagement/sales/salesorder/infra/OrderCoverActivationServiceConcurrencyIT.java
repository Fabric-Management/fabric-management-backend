package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Runs the actual activation service and confirmation/enrolment after both real lock orders. */
@ResourceLock("sales-order-creation-sequence")
class OrderCoverActivationServiceConcurrencyIT extends OrderCoverIntegrationSupport {
  @Test
  void insertFirstCommitsAsLegacyAndTheNextCommittedOrderIsGoverned() throws Exception {
    CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var insert =
          executor.submit(
              () ->
                  inActor(
                      () ->
                          tx(
                              () -> {
                                UUID orderId = draft(1);
                                held.countDown();
                                awaitRelease(release);
                                return orderId;
                              })));
      assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
      var activate = executor.submit(() -> inActor(() -> activation.activate()));
      awaitBoundaryWait(activate);
      release.countDown();
      UUID before = insert.get(10, TimeUnit.SECONDS);
      var boundary = activate.get(10, TimeUnit.SECONDS);
      sales.confirmOrder(before, actor.getId());
      assertThat(regime(before)).isEqualTo("LEGACY");
      assertThat(
              jdbc.queryForObject(
                  "select creation_seq from sales_ord.sales_order where id=?", Long.class, before))
          .isLessThanOrEqualTo(boundary.boundarySeq());
      var after = governed(1);
      assertThat(regime(after.orderId())).isEqualTo("GOVERNED");
      assertThat(
              jdbc.queryForObject(
                  "select creation_seq from sales_ord.sales_order where id=?",
                  Long.class,
                  after.orderId()))
          .isGreaterThan(boundary.boundarySeq());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void activationFirstBlocksInsertUntilCommitAndConfirmationPersistsGoverned() throws Exception {
    CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var activate =
          executor.submit(
              () ->
                  inActor(
                      () ->
                          tx(
                              () -> {
                                var boundary = activation.activate();
                                held.countDown();
                                awaitRelease(release);
                                return boundary;
                              })));
      assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
      var insert = executor.submit(() -> inActor(() -> draft(1)));
      awaitBoundaryWait(insert);
      release.countDown();
      var boundary = activate.get(10, TimeUnit.SECONDS);
      UUID orderId = insert.get(10, TimeUnit.SECONDS);
      sales.confirmOrder(orderId, actor.getId());
      awaitCover(orderId);
      assertThat(regime(orderId)).isEqualTo("GOVERNED");
      assertThat(
              jdbc.queryForObject(
                  "select creation_seq from sales_ord.sales_order where id=?", Long.class, orderId))
          .isGreaterThan(boundary.boundarySeq());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private String regime(UUID orderId) {
    return jdbc.queryForObject(
        "select cover_regime from sales_ord.sales_order where id=?", String.class, orderId);
  }

  private void awaitBoundaryWait(Future<?> worker) {
    String key = tenant + ":ORDER_COVER_ACTIVATION";
    await()
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () -> {
              assertThat(worker).isNotDone();
              assertThat(
                      jdbc.queryForObject(
                          """
                          select count(*) from pg_locks where locktype='advisory' and not granted
                          and classid=(('x'||substr(md5(?),1,8))::bit(32)::bigint)
                          and objid=(('x'||substr(md5(?),9,8))::bit(32)::bigint)
                          """,
                          Integer.class,
                          key,
                          key))
                  .isPositive();
            });
  }

  private static void awaitRelease(CountDownLatch release) {
    try {
      if (!release.await(12, TimeUnit.SECONDS))
        throw new AssertionError("Activation holder timed out");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new AssertionError(error);
    }
  }
}
