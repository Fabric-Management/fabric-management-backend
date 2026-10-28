package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CollectionReconciliationExecutorTest {
  CollectionReconciliationExecutor executor = new CollectionReconciliationExecutor(2);

  @AfterEach
  void stop() {
    executor.shutdown();
  }

  private static void waitFor(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("never released");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void theWorkRunsOnAWorkerThreadAndNotOnTheCaller() throws Exception {
    var thread = new CompletableFuture<String>();
    boolean taken =
        executor.submit(UUID.randomUUID(), () -> thread.complete(Thread.currentThread().getName()));
    assertThat(taken).isTrue();
    assertThat(thread.get(10, TimeUnit.SECONDS)).startsWith("collection-reconcile-");
    assertThat(thread.get()).isNotEqualTo(Thread.currentThread().getName());
  }

  @Test
  void aTenantStillRunningIsDeclinedUntilItsRunHasFinished() throws Exception {
    UUID tenant = UUID.randomUUID();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    assertThat(
            executor.submit(
                tenant,
                () -> {
                  started.countDown();
                  waitFor(release);
                }))
        .isTrue();
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(executor.submit(tenant, () -> {})).isFalse();
    release.countDown();
    await().atMost(10, TimeUnit.SECONDS).until(() -> executor.submit(tenant, () -> {}));
  }

  @Test
  void aTenantWhoseRunFailedIsNotStuckAsRunning() {
    UUID tenant = UUID.randomUUID();
    assertThat(
            executor.submit(
                tenant,
                () -> {
                  throw new IllegalStateException("boom");
                }))
        .isTrue();
    await().atMost(10, TimeUnit.SECONDS).until(() -> executor.submit(tenant, () -> {}));
  }

  @Test
  void aSlowTenantDoesNotHoldUpAnotherOne() throws Exception {
    var slowStarted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var otherRan = new CountDownLatch(1);
    executor.submit(
        UUID.randomUUID(),
        () -> {
          slowStarted.countDown();
          waitFor(release);
        });
    assertThat(slowStarted.await(10, TimeUnit.SECONDS)).isTrue();
    executor.submit(UUID.randomUUID(), otherRan::countDown);
    assertThat(otherRan.await(10, TimeUnit.SECONDS)).isTrue();
    release.countDown();
  }

  @Test
  void withOneWorkerTheSecondTenantWaitsItsTurnInsteadOfBeingDropped() throws Exception {
    executor.shutdown();
    executor = new CollectionReconciliationExecutor(0);
    var firstStarted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var secondRan = new AtomicBoolean();
    executor.submit(
        UUID.randomUUID(),
        () -> {
          firstStarted.countDown();
          waitFor(release);
        });
    assertThat(firstStarted.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(executor.submit(UUID.randomUUID(), () -> secondRan.set(true))).isTrue();
    assertThat(secondRan).isFalse();
    release.countDown();
    await().atMost(10, TimeUnit.SECONDS).untilTrue(secondRan);
  }

  @Test
  void afterShutdownNothingIsAcceptedAndNoTenantIsLeftMarkedAsRunning() {
    executor.shutdown();
    UUID tenant = UUID.randomUUID();
    assertThat(executor.submit(tenant, () -> {})).isFalse();
    assertThat(executor.submit(tenant, () -> {})).isFalse();
  }
}
