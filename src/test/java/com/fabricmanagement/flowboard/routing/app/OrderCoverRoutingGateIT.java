package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.task.app.DecisionBlockedException;
import com.fabricmanagement.flowboard.task.app.OrderCoverAuthorisation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderCoverRoutingGateIT extends RoutingIntegrationSupport {
  @Autowired OrderCoverAuthorisation authorisation;

  @Test
  void orderCoverPoolRetainsBothRequiredWritePermissions() {
    assertThat(RoutingPoolKey.ORDER_COVER.requiredPermissions())
        .extracting(permission -> permission.name())
        .containsExactlyInAnyOrder("FLOWBOARD_WRITE", "SALES_WRITE");
  }

  @Test
  void removedPoolMemberCannotUseTheStillPresentDirectAssignment() {
    var actor = user("Assigned", "flowboard:write", "sales:write");
    var task = task(actor.getId());
    configure(actor.getId());
    evaluate(task);
    assertThat(assignees(task)).containsExactly(actor.getId());
    configure();
    TenantContext.setCurrentUserId(actor.getId());

    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      authorisation.assertFirstExecution(task, actor.getId());
                      return null;
                    }))
        .isInstanceOfSatisfying(
            DecisionBlockedException.class,
            failure ->
                assertThat(failure.getDetails().toString()).contains("OUTSIDE_ROUTING_POOL"));
  }

  @Test
  void settlementSharedPoolLockSerializesAConcurrentRemoval() throws Exception {
    var actor = user("Concurrent", "flowboard:write", "sales:write");
    configure(actor.getId());
    CountDownLatch sharedHeld = new CountDownLatch(1);
    CountDownLatch releaseSettlement = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var settlement =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                repository.lockPool(tenant, RoutingPoolKey.ORDER_COVER, false);
                                assertThat(
                                        repository
                                            .members(
                                                tenant,
                                                repository
                                                    .pool(tenant, RoutingPoolKey.ORDER_COVER)
                                                    .orElseThrow()
                                                    .id())
                                            .stream()
                                            .anyMatch(
                                                member ->
                                                    member.active()
                                                        && member.userId().equals(actor.getId())))
                                    .isTrue();
                                sharedHeld.countDown();
                                await(releaseSettlement);
                                return true;
                              })));
      assertThat(sharedHeld.await(10, TimeUnit.SECONDS)).isTrue();
      var removal =
          executor.submit(
              () ->
                  inTenant(
                      () -> {
                        configure();
                        return true;
                      }));
      Thread.sleep(150);
      assertThat(removal).isNotDone();
      releaseSettlement.countDown();
      assertThat(settlement.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(removal.get(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      releaseSettlement.countDown();
    }

    assertThat(
            repository
                .members(
                    tenant, repository.pool(tenant, RoutingPoolKey.ORDER_COVER).orElseThrow().id())
                .stream()
                .noneMatch(member -> member.active() && member.userId().equals(actor.getId())))
        .isTrue();
  }
}
