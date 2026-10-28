package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlanState;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class StockUnitCutPlanIT extends StockCutITSupport {
  @Test
  void displayedSourcesAreHeldBeforeApplyAndAnotherActorCannotTakeThem() {
    var order = createExact();
    var opened = openSources(order);
    var held = pick(opened);
    assertThat(held.evaluation().sourceQuantity()).isEqualByComparingTo("2125");
    assertThat(held.evaluation().deliveryQuantity()).isEqualByComparingTo("2050");
    assertThat(held.evaluation().remainderQuantity()).isEqualByComparingTo("75");
    assertThat(held.grants()).hasSize(17);
    assertThat(
            in(tenant, () -> previews.preview(request(), otherActor))
                .lots()
                .getFirst()
                .free()
                .canonical())
        .isEqualByComparingTo("875");
    assertThatThrownBy(() -> in(tenant, () -> cutSessions.status(opened.selectionId(), otherActor)))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    assertThatThrownBy(() -> in(otherTenant, () -> cutSessions.status(opened.selectionId(), actor)))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    in(
        tenant,
        () ->
            cutSessions.close(
                held.selectionId(),
                new StockUnitCutSessionDtos.Tokens(
                    held.revision(),
                    held.grants().stream().map(StockUnitCutSessionDtos.Grant::token).toList()),
                actor));
    assertThat(
            in(tenant, () -> previews.preview(request(), otherActor))
                .lots()
                .getFirst()
                .free()
                .canonical())
        .isEqualByComparingTo("3000");
  }

  @Test
  void lostApplyReplyReplaysOnePlanAndLateCloseDoesNotReleaseItsPermanentSources() {
    var order = createExact();
    var held = pick(openSources(order));
    var request = applyRequest(held);
    var saved = in(tenant, () -> cutSessions.apply(held.selectionId(), request, actor));
    assertThat(in(tenant, () -> cutSessions.apply(held.selectionId(), request, actor)))
        .isEqualTo(saved);
    assertThat(saved.state()).isEqualTo(StockUnitCutPlanState.PREPARED);
    assertThat(saved.actual()).isNull();
    in(
        tenant,
        () ->
            cutSessions.close(
                held.selectionId(),
                new StockUnitCutSessionDtos.Tokens(
                    held.revision(),
                    held.grants().stream().map(StockUnitCutSessionDtos.Grant::token).toList()),
                actor));
    assertThat(in(tenant, () -> holds.heldPieceIds(order.getLines().getFirst().getId())))
        .hasSize(17);
    assertThat(
            in(
                tenant,
                () ->
                    cutPlans.list(
                        new com.fabricmanagement.production.core.stockunit.api
                            .StockUnitCutTargetPort.Target(
                            com.fabricmanagement.production.core.stockunit.api
                                .StockUnitCutTargetPort.TargetType.SALES_ORDER,
                            order.getId(),
                            order.getLines().getFirst().getId()),
                        actor)))
        .hasSize(1);
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        cutSessions.apply(
                            held.selectionId(),
                            new StockUnitCutSessionDtos.Apply(
                                request.operationId(),
                                request.expectedRevision(),
                                request.expectedDocumentVersion() + 1,
                                request.tokens()),
                            actor)))
        .hasMessageContaining("different cut command");
  }

  @Test
  void changedMeasurementsCannotBeAppliedAndRevokedPermissionsCannotRecordACut() {
    var order = createExact();
    var held = pick(openSources(order));
    system.executeInTransaction(
        jdbc -> {
          jdbc.update("update production.stock_unit set length=130 where id=?", rolls.get(16));
          jdbc.update("update production.stock_unit set length=125 where id=?", rolls.get(16));
          return null;
        });
    var changed = in(tenant, () -> cutSessions.status(held.selectionId(), actor));
    assertThat(changed.evaluation()).isNull();
    assertThatThrownBy(
            () ->
                in(tenant, () -> cutSessions.apply(held.selectionId(), applyRequest(held), actor)))
        .hasMessageContaining("Reload");
    var refreshed =
        in(
            tenant,
            () ->
                cutSessions.pick(
                    held.selectionId(),
                    new StockUnitCutSessionDtos.Pick(
                        changed.revision(), held.sourceIds(), rolls.get(16)),
                    actor));
    var plan = apply(refreshed);
    when(cutPermissions.evaluateFresh(any(), any(), anyList(), any()))
        .thenReturn(new PermissionResult(Map.of(), false));
    when(permissions.resolve(eq(tenant), eq(actor), eq("write"), any()))
        .thenReturn(SalesAccessScopeResolver.AccessScope.denied());
    assertThat(in(tenant, () -> cutPlans.get(plan.planId(), actor)).capabilities())
        .noneMatch(capability -> capability.allowed());
    assertThatThrownBy(() -> command(plan, StockUnitCutPlanDtos.Action.APPROVE, null))
        .hasMessageContaining("no longer permits");
  }

  @Test
  void twoOrdersRacingForTheSameWholeSourcesLeaveOnlyOneProvisionalOwner() throws Exception {
    var first = openSources(createExact());
    var second = openSources(createExact());
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var results =
          List.of(first, second).stream()
              .map(
                  open ->
                      pool.submit(
                          () -> {
                            start.await(10, TimeUnit.SECONDS);
                            try {
                              pick(open);
                              return true;
                            } catch (
                                com.fabricmanagement.common.infrastructure.web.exception
                                        .DomainException
                                    conflict) {
                              return false;
                            }
                          }))
              .toList();
      start.countDown();
      assertThat(
              List.of(
                  results.get(0).get(20, TimeUnit.SECONDS),
                  results.get(1).get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      var one = in(tenant, () -> cutSessions.status(first.selectionId(), actor));
      var two = in(tenant, () -> cutSessions.status(second.selectionId(), actor));
      assertThat(one.grants().size() + two.grants().size()).isEqualTo(17);
    } finally {
      pool.shutdownNow();
    }
  }
}
