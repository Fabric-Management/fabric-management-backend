package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.production.core.inventory.api.facade.InventoryFacade;
import com.fabricmanagement.production.core.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.core.stockunit.domain.*;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ExactMetreFulfilmentIT extends StockCutITSupport {
  @MockitoSpyBean InventoryFacade inventory;

  @Test
  void verifiedCutCreatesOneDeliveryAndOneRemainderWithoutChangingDemandOrProductionAverage() {
    var order = createExact();
    var plan = apply(pick(openSources(order)));
    assertThat(in(tenant, () -> cutPlans.hasUnfinishedPlan(order.getLines().getFirst().getId())))
        .isTrue();
    releaseForExecution(order);
    var approved = command(plan, StockUnitCutPlanDtos.Action.APPROVE, null);
    var recorded = command(approved, StockUnitCutPlanDtos.Action.RECORD, measured());
    assertThat(recorded.state()).isEqualTo(StockUnitCutPlanState.RECORDED);
    assertThat(recorded.deliveryStockUnitId()).isNull();
    assertThat(recorded.remainderStockUnitId()).isNull();
    assertThat(in(tenant, () -> holds.heldPieceIds(order.getLines().getFirst().getId()))).isEmpty();
    assertThat(
            in(
                tenant,
                () -> allocations.activeForLine(tenant, order.getLines().getFirst().getId())))
        .hasSize(17);
    assertThat(
            in(
                tenant,
                () ->
                    cutPlans.exactDeliveryReady(
                        order.getLines().getFirst().getId(), new BigDecimal("2050"))))
        .isFalse();
    var request =
        new StockUnitCutPlanDtos.Command(UUID.randomUUID(), recorded.version(), null, null);
    var completed =
        in(
            tenant,
            () ->
                cutPlans.command(
                    recorded.planId(), StockUnitCutPlanDtos.Action.VERIFY, request, actor));
    assertThat(
            in(
                tenant,
                () ->
                    cutPlans.command(
                        recorded.planId(), StockUnitCutPlanDtos.Action.VERIFY, request, actor)))
        .isEqualTo(completed);
    assertThat(completed.state()).isEqualTo(StockUnitCutPlanState.COMPLETED);
    in(
        tenant,
        () -> {
          var parent =
              stockUnits.findByIdAndTenantIdAndIsActiveTrue(rolls.get(16), tenant).orElseThrow();
          var delivery =
              stockUnits
                  .findByIdAndTenantIdAndIsActiveTrue(completed.deliveryStockUnitId(), tenant)
                  .orElseThrow();
          var remainder =
              stockUnits
                  .findByIdAndTenantIdAndIsActiveTrue(completed.remainderStockUnitId(), tenant)
                  .orElseThrow();
          assertThat(parent.getStatus()).isEqualTo(StockUnitStatus.DEPLETED);
          assertThat(parent.getCurrentWeight()).isEqualByComparingTo("0");
          assertThat(parent.getInitialLength()).isEqualByComparingTo("125");
          assertThat(delivery.getLength()).isEqualByComparingTo("50");
          assertThat(delivery.getCurrentWeight()).isEqualByComparingTo("12");
          assertThat(delivery.getStatus()).isEqualTo(StockUnitStatus.RESERVED);
          assertThat(remainder.getLength()).isEqualByComparingTo("75");
          assertThat(remainder.getCurrentWeight()).isEqualByComparingTo("18");
          assertThat(remainder.getStatus()).isEqualTo(StockUnitStatus.AVAILABLE);
          assertThat(remainder.getSourceId()).isEqualTo(parent.getId());
          assertThat(delivery.getSourceId()).isEqualTo(parent.getId());
          assertThat(delivery.getBarcode()).isNotEqualTo(remainder.getBarcode());
          var line =
              lineRows
                  .findByTenantIdAndId(tenant, order.getLines().getFirst().getId())
                  .orElseThrow();
          assertThat(line.getRequestedQty()).isEqualByComparingTo("2050");
          assertThat(line.getUnitPriceAmount().multiply(line.getRequestedQty()))
              .isEqualByComparingTo("17630");
          var held = allocations.activeForLine(tenant, line.getId());
          assertThat(held).hasSize(17).noneMatch(row -> row.stockUnitId().equals(parent.getId()));
          assertThat(
                  held.stream()
                      .map(
                          com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort
                                  .PieceAllocationView
                              ::quantity)
                      .reduce(BigDecimal.ZERO, BigDecimal::add))
              .isEqualByComparingTo("2050");
          assertThat(cutPlans.exactDeliveryReady(line.getId(), new BigDecimal("2050"))).isTrue();
          assertThat(balances.findByBatchIdAndLocationIdIsNull(batch).orElseThrow().getQuantity())
              .isEqualByComparingTo("3000");
          return null;
        });
    var rows =
        system.executeInTransaction(
            jdbc ->
                jdbc.queryForObject(
                    "select count(*) from production.production_execution_inventory_transaction where tenant_id=? and idempotency_key like ?",
                    Integer.class,
                    tenant,
                    "CUT:" + completed.planId() + ":%"));
    assertThat(rows).isEqualTo(3);
  }

  @Test
  void ledgerFailureRollsBackChildrenAllocationsAndParentReplacementTogether() {
    var order = createExact();
    var plan = apply(pick(openSources(order)));
    releaseForExecution(order);
    var recorded =
        command(
            command(plan, StockUnitCutPlanDtos.Action.APPROVE, null),
            StockUnitCutPlanDtos.Action.RECORD,
            measured());
    doThrow(new IllegalStateException("Inventory ledger unavailable"))
        .when(inventory)
        .logTransaction(
            any(),
            any(),
            eq(InventoryTransactionType.SPLIT_IN),
            any(),
            anyString(),
            nullable(UUID.class),
            any(),
            anyString(),
            anyString(),
            any(),
            anyString());
    assertThatThrownBy(() -> command(recorded, StockUnitCutPlanDtos.Action.VERIFY, null))
        .hasMessageContaining("ledger unavailable");
    in(
        tenant,
        () -> {
          var current = cutPlans.get(recorded.planId(), actor);
          assertThat(current.state()).isEqualTo(StockUnitCutPlanState.RECORDED);
          assertThat(current.deliveryStockUnitId()).isNull();
          var parent =
              stockUnits.findByIdAndTenantIdAndIsActiveTrue(rolls.get(16), tenant).orElseThrow();
          assertThat(parent.getStatus()).isEqualTo(StockUnitStatus.RESERVED);
          assertThat(parent.getCurrentWeight()).isEqualByComparingTo("30");
          assertThat(allocations.activeForLine(tenant, order.getLines().getFirst().getId()))
              .hasSize(17);
          return null;
        });
    assertThat(
            system.<Integer>executeInTransaction(
                jdbc ->
                    jdbc.queryForObject(
                        "select count(*) from production.stock_unit where tenant_id=? and source_type='SPLIT' and source_id=?",
                        Integer.class,
                        tenant,
                        rolls.get(16))))
        .isZero();
  }

  @Test
  void cancellationAfterRecordingKeepsTheCutSourceAndVerificationProducesHeldRealOutputs() {
    var order = createExact();
    var plan = apply(pick(openSources(order)));
    releaseForExecution(order);
    var recorded =
        command(
            command(plan, StockUnitCutPlanDtos.Action.APPROVE, null),
            StockUnitCutPlanDtos.Action.RECORD,
            measured());
    in(tenant, () -> orders.cancelOrder(order.getId(), actor));
    var cancelled = in(tenant, () -> cutPlans.get(recorded.planId(), actor));
    assertThat(cancelled.state()).isEqualTo(StockUnitCutPlanState.DISPUTED);
    assertThat(
            in(
                tenant,
                () -> allocations.activeForLine(tenant, order.getLines().getFirst().getId())))
        .singleElement()
        .satisfies(row -> assertThat(row.stockUnitId()).isEqualTo(rolls.get(16)));
    var checked = command(cancelled, StockUnitCutPlanDtos.Action.CORRECT, measured());
    var completed = command(checked, StockUnitCutPlanDtos.Action.VERIFY, null);
    in(
        tenant,
        () -> {
          var parent =
              stockUnits.findByIdAndTenantIdAndIsActiveTrue(rolls.get(16), tenant).orElseThrow();
          assertThat(parent.getStatus()).isEqualTo(StockUnitStatus.DEPLETED);
          for (UUID id :
              java.util.List.of(
                  completed.deliveryStockUnitId(), completed.remainderStockUnitId())) {
            var child = stockUnits.findByIdAndTenantIdAndIsActiveTrue(id, tenant).orElseThrow();
            assertThat(child.getStatus()).isEqualTo(StockUnitStatus.ON_HOLD);
            assertThat(child.getQualityDisposition())
                .isEqualTo(QualityDisposition.PENDING_INSPECTION);
          }
          assertThat(allocations.activeForLine(tenant, order.getLines().getFirst().getId()))
              .isEmpty();
          return null;
        });
  }

  @Test
  void anExpiredSelectionCanCloseWithoutBlockingTheForm() {
    var held = pick(openSources(createExact()));
    system.executeInTransaction(
        jdbc -> {
          // ck_live_edit_lease_period: acquired_at <= renewed_at < expires_at, so move all three.
          jdbc.update(
              "update common_infrastructure.live_edit_lease set acquired_at=now()-interval '2"
                  + " minutes', renewed_at=now()-interval '2 minutes', expires_at=now()-interval '1"
                  + " second' where session_id=?",
              held.sessionId());
          return null;
        });
    var closed =
        in(
            tenant,
            () ->
                cutSessions.close(
                    held.selectionId(),
                    new StockUnitCutSessionDtos.Tokens(
                        held.revision(),
                        held.grants().stream().map(StockUnitCutSessionDtos.Grant::token).toList()),
                    actor));
    assertThat(closed.state()).isEqualTo(StockUnitCutSessionDtos.State.ENDED);
    assertThat(closed.grants()).isEmpty();
  }
}
