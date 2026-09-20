package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.sales.salesorder.app.OrderCoverService;
import com.fabricmanagement.sales.salesorder.dto.ConfirmProductionCoverPayload;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

/** Exercises the routing gate as part of the real HTTP/controller → settlement transaction. */
class OrderCoverRoutingSettlementIT extends OrderCoverIntegrationSupport {
  @Autowired CacheManager caches;
  @MockitoSpyBean OrderCoverService coverService;

  @ParameterizedTest
  @ValueSource(strings = {"flowboard", "sales"})
  void revokedWriteGrantIsRefusedEvenWhenTheCachedAnswerStillGrantsIt(String resource)
      throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    assertThat(
            permissionEvaluator
                .evaluate(tenant, actor.getRole().getRoleCode(), List.of(), actor.getId())
                .can(resource, "write"))
        .isTrue();
    jdbc.update(
        "update common_user.permission_template set is_active=false where tenant_id=? and"
            + " role_code=? and resource=? and action='write'",
        tenant,
        actor.getRole().getRoleCode(),
        resource);
    assertThat(
            permissionEvaluator
                .evaluate(tenant, actor.getRole().getRoleCode(), List.of(), actor.getId())
                .can(resource, "write"))
        .isTrue();
    postTransition(cover, command)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DECISION_BLOCKED"))
        .andExpect(jsonPath("$.reason.code").value("PERMISSION_DENIED"))
        .andExpect(jsonPath("$.reason.parameters.requiredPermission").value(resource + ":write"));
    assertThat(attempts(command.idempotencyKey())).isZero();
    assertNoSettlement();
  }

  @Test
  void actorOutsideFreshSalesWriteScopeCannotSettleAReadableOrder() throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    jdbc.update(
        "update common_user.permission_template set data_scope='OWN' where tenant_id=? and"
            + " role_code=? and resource='sales' and action='write'",
        tenant,
        actor.getRole().getRoleCode());
    jdbc.update(
        "update sales_ord.sales_order set created_by=? where id=?",
        UUID.randomUUID(),
        cover.orderId());
    postTransition(cover, command)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason.code").value("PERMISSION_DENIED"));
    assertNoSettlement();
    assertThat(attempts(command.idempotencyKey())).isZero();
  }

  @Test
  void deactivatedDirectAssigneeCannotSettle() throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    jdbc.update("update common_user.common_user set is_active=false where id=?", actor.getId());
    postTransition(cover, command)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason.code").value("PERMISSION_DENIED"));
    assertNoSettlement();
    assertThat(attempts(command.idempotencyKey())).isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void directAssignmentIsRequiredEvenWithPermissionsAndPoolMembership(boolean assignedElsewhere)
      throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    if (assignedElsewhere) {
      var other = user("Other", "sales:read", "sales:write", "flowboard:read", "flowboard:write");
      jdbc.update(
          "update flowboard.task_assignee set user_id=? where task_id=? and is_active",
          other.getId(),
          cover.taskId());
    } else {
      jdbc.update(
          "update flowboard.task_assignee set is_active=false where task_id=?", cover.taskId());
    }
    postTransition(cover, command)
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.reason.code")
                .value(assignedElsewhere ? "ASSIGNED_ELSEWHERE" : "UNASSIGNED"));
    assertNoSettlement();
    assertThat(attempts(command.idempotencyKey())).isZero();
  }

  @Test
  void removedPoolMemberCannotSettleBeforeAssignmentRepair() throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    configurePool();
    assertThat(
            jdbc.queryForList(
                "select user_id from flowboard.task_assignee where task_id=? and is_active",
                UUID.class,
                cover.taskId()))
        .containsExactly(actor.getId());
    postTransition(cover, command)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason.code").value("OUTSIDE_ROUTING_POOL"));
    assertNoSettlement();
    assertThat(attempts(command.idempotencyKey())).isZero();
  }

  @Test
  void originalActorCanReplayAfterLosingAssignmentPoolMembershipAndWriteGrants() throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    var first = settle(cover, command);
    configurePool();
    jdbc.update(
        "update flowboard.task_assignee set is_active=false where task_id=?", cover.taskId());
    jdbc.update(
        "update common_user.permission_template set is_active=false where tenant_id=? and"
            + " role_code=? and action='write'",
        tenant,
        actor.getRole().getRoleCode());
    caches.getCache("permissions").evict(tenant + "_" + actor.getId());
    postTransition(cover, command)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.replayed").value(true))
        .andExpect(jsonPath("$.data.result.id").value(first.result().id().toString()));
    assertThat(count("production.prod_work_order")).isEqualTo(1);
    assertThat(count("sales_ord.order_cover_result")).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unreadableSubjectReturns404BeforeFirstExecutionOrReplay(boolean replay) throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    if (replay) settle(cover, command);
    jdbc.update(
        "update common_user.permission_template set data_scope='OWN' where tenant_id=? and"
            + " role_code=? and resource='sales' and action='read'",
        tenant,
        actor.getRole().getRoleCode());
    jdbc.update(
        "update sales_ord.sales_order set created_by=? where id=?",
        UUID.randomUUID(),
        cover.orderId());
    caches.getCache("permissions").evict(tenant + "_" + actor.getId());
    postTransition(cover, command)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.data").doesNotExist());
    assertThat(count("production.prod_work_order")).isEqualTo(replay ? 1 : 0);
    assertThat(attempts(command.idempotencyKey())).isEqualTo(replay ? 1 : 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void concurrentPoolRemovalAndRealSettlementRespectWhicheverTransactionWins(
      boolean settlementFirst) throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      if (settlementFirst) {
        OrderCoverService target = AopTestUtils.getUltimateTargetObject(coverService);
        doAnswer(
                invocation -> {
                  // The genuine authorisation gate has already acquired and checked the shared pool
                  // lock.
                  held.countDown();
                  awaitRelease(release);
                  return invocation.callRealMethod();
                })
            .when(target)
            .confirm(
                eq(tenant),
                eq(cover.orderId()),
                eq(actor.getId()),
                any(ConfirmProductionCoverPayload.class));
        var settlement = executor.submit(() -> inActor(() -> settle(cover, command)));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        var removal =
            executor.submit(
                () ->
                    inActor(
                        () -> {
                          configurePool();
                          return true;
                        }));
        awaitPoolWait(removal);
        release.countDown();
        assertThat(settlement.get(15, TimeUnit.SECONDS).result().id()).isNotNull();
        assertThat(removal.get(15, TimeUnit.SECONDS)).isTrue();
        assertThat(count("sales_ord.order_cover_result")).isEqualTo(1);
        assertThat(count("production.prod_work_order")).isEqualTo(1);
      } else {
        var removal =
            executor.submit(
                () ->
                    inActor(
                        () ->
                            tx(
                                () -> {
                                  configurePool();
                                  held.countDown();
                                  awaitRelease(release);
                                  return true;
                                })));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        var settlement = executor.submit(() -> inActor(() -> settle(cover, command)));
        awaitPoolWait(settlement);
        release.countDown();
        assertThat(removal.get(15, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> settlement.get(15, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .satisfies(
                failure -> {
                  assertThat(failure.getCause()).isInstanceOf(DomainException.class);
                  var denied = (DomainException) failure.getCause();
                  assertThat(denied.getHttpStatus()).isEqualTo(403);
                  assertThat(denied.getDetails().get("reason").toString())
                      .contains("OUTSIDE_ROUTING_POOL");
                });
        assertNoSettlement();
        assertThat(attempts(command.idempotencyKey())).isZero();
      }
      assertThat(
              routing.members(
                  tenant, routing.pool(tenant, RoutingPoolKey.ORDER_COVER).orElseThrow().id()))
          .noneMatch(member -> member.active() && member.userId().equals(actor.getId()));
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private void awaitPoolWait(Future<?> worker) {
    await()
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () -> {
              assertThat(worker).isNotDone();
              assertThat(
                      jdbc.queryForObject(
                          """
                          select count(*) from pg_locks
                          where locktype='advisory' and not granted
                            and classid = (('x'||substr(md5(?),1,8))::bit(32)::bigint)
                            and objid = (('x'||substr(md5(?),9,8))::bit(32)::bigint)
                          """,
                          Integer.class,
                          tenant + ":ORDER_COVER",
                          tenant + ":ORDER_COVER"))
                  .isPositive();
            });
  }

  private static void awaitRelease(CountDownLatch release) {
    try {
      if (!release.await(12, TimeUnit.SECONDS)) throw new AssertionError("Race holder timed out");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new AssertionError(error);
    }
  }
}
