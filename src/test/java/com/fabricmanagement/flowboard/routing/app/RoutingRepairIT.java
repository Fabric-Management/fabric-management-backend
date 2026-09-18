package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.flowboard.routing.domain.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class RoutingRepairIT extends RoutingIntegrationSupport {
  @Test
  void unchangedRepairIsIdempotentAndRecurrenceCreatesNewFailure() {
    var member = user("Member", "flowboard:write", "sales:write");
    var task = task(member.getId());
    configure();
    evaluate(task);
    var original = failure(task);
    var again = repair.repair(tenant, RoutingPoolKey.ORDER_COVER, false);
    assertThat(again.failuresOpened()).isZero();
    assertThat(again.failuresResolved()).isZero();
    assertThat(count("flowboard.routing_failure_alert")).isZero();
    configure(member.getId());
    assertThat(repair.repair(tenant, RoutingPoolKey.ORDER_COVER, false).failuresResolved())
        .isEqualTo(1);
    configure();
    repair.repair(tenant, RoutingPoolKey.ORDER_COVER, false);
    assertThat(failure(task)).isNotEqualTo(original);
    assertThat(count("flowboard.routing_failure")).isEqualTo(2);
  }

  @Test
  void concurrentEmptyPoolRepairsAndConcurrentResolutionsAreUnique() throws Exception {
    var member = user("Member", "flowboard:write", "sales:write");
    var task = task(member.getId());
    configure();
    race(() -> evaluate(task), () -> evaluate(task));
    assertThat(repository.openFailures(tenant, task.getId())).hasSize(1);
    configure(member.getId());
    race(() -> evaluate(task), () -> evaluate(task));
    assertThat(count("flowboard.routing_failure_resolution")).isEqualTo(1);
    assertThat(assignees(task)).containsExactly(member.getId());
  }

  @Test
  void revisionScanIncludesMissingAndNullAndResumesInterruptedRepair() {
    var member = user("Member", "flowboard:write", "sales:write");
    var absent = task(member.getId());
    var missing = task(member.getId());
    evaluate(absent);
    configure(member.getId());
    assertThat(repository.repairTasks(tenant, RoutingPoolKey.ORDER_COVER, true))
        .containsExactlyInAnyOrder(absent.getId(), missing.getId());
    evaluate(absent); // Simulated interrupted run: only the first task committed.
    assertThat(repository.repairTasks(tenant, RoutingPoolKey.ORDER_COVER, true))
        .containsExactly(missing.getId());
    var result = repair.repair(tenant, RoutingPoolKey.ORDER_COVER, true);
    assertThat(result.evaluated()).isEqualTo(1);
    assertThat(repository.repairTasks(tenant, RoutingPoolKey.ORDER_COVER, true)).isEmpty();
    assertThat(assignees(missing)).containsExactly(member.getId());
  }

  @Test
  void firstEvaluationBeforeFirstPutCannotStrandNoPool() throws Exception {
    var member = user("Member", "flowboard:write", "sales:write");
    var task = task(member.getId());
    CountDownLatch evaluated = new CountDownLatch(1),
        release = new CountDownLatch(1),
        writerStarted = new CountDownLatch(1);
    var writerPid = new java.util.concurrent.atomic.AtomicInteger();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                var result = evaluate(task);
                                evaluated.countDown();
                                await(release);
                                return result;
                              })));
      await(evaluated, first);
      var writer =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                writerPid.set(
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                                writerStarted.countDown();
                                configure(member.getId());
                                return true;
                              })));
      await(writerStarted, writer);
      awaitBlocked(writerPid, writer);
      assertThat(writer.isDone()).isFalse();
      release.countDown();
      first.get(30, TimeUnit.SECONDS);
      writer.get(30, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.close();
    }
    repair.repair(tenant, RoutingPoolKey.ORDER_COVER, true);
    assertThat(assignees(task)).containsExactly(member.getId());
    assertThat(repository.openFailures(tenant, task.getId())).isEmpty();
  }

  @Test
  void firstPutBeforeEvaluationIsReadAfterLockWait() throws Exception {
    var member = user("Member", "flowboard:write", "sales:write");
    var task = task(member.getId());
    CountDownLatch configured = new CountDownLatch(1),
        release = new CountDownLatch(1),
        readerStarted = new CountDownLatch(1);
    var readerPid = new java.util.concurrent.atomic.AtomicInteger();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var writer =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                configure(member.getId());
                                configured.countDown();
                                await(release);
                                return true;
                              })));
      await(configured, writer);
      var reader =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                readerPid.set(
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                                readerStarted.countDown();
                                return evaluate(task);
                              })));
      await(readerStarted, reader);
      awaitBlocked(readerPid, reader);
      assertThat(reader.isDone()).isFalse();
      release.countDown();
      writer.get(30, TimeUnit.SECONDS);
      reader.get(30, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.close();
    }
    assertThat(assignees(task)).containsExactly(member.getId());
    assertThat(count("flowboard.routing_failure")).isZero();
    assertThat(repository.repairTasks(tenant, RoutingPoolKey.ORDER_COVER, true)).isEmpty();
  }

  @Test
  void configurationRacingExistingEvaluationLeavesAnExplicitRepairRevision() throws Exception {
    var member = user("Member", "flowboard:write", "sales:write");
    var task = task(member.getId());
    configure();
    evaluate(task);
    CountDownLatch evaluated = new CountDownLatch(1), release = new CountDownLatch(1);
    var writerPid = new java.util.concurrent.atomic.AtomicInteger();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var evaluation =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                var result = evaluate(task);
                                evaluated.countDown();
                                await(release);
                                return result;
                              })));
      await(evaluated, evaluation);
      var writer =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                writerPid.set(
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                                configuration.configure(
                                    tenant, RoutingPoolKey.ORDER_COVER, 1L, Set.of(member.getId()));
                                return true;
                              })));
      awaitBlocked(writerPid, writer);
      release.countDown();
      evaluation.get(30, TimeUnit.SECONDS);
      writer.get(30, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.close();
    }
    assertThat(repository.repairTasks(tenant, RoutingPoolKey.ORDER_COVER, true))
        .containsExactly(task.getId());
    repair.repair(tenant, RoutingPoolKey.ORDER_COVER, true);
    assertThat(assignees(task)).containsExactly(member.getId());
    assertThat(repository.repairTasks(tenant, RoutingPoolKey.ORDER_COVER, true)).isEmpty();
  }
}
