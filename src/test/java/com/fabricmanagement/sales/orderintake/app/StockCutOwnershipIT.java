package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.core.stockunit.app.StockUnitService;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlanState;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitHeldException;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StockCutOwnershipIT extends StockCutITSupport {
  @Autowired StockUnitService stock;

  @Test
  void confirmedMetreOnlyOrderCanApplyItsOwnProvenSourcesAndReplay() {
    var order = createExact();
    // The order was confirmed when no finished sources were available; stock arrived later.
    in(
        tenant,
        () -> {
          var stored = orderRows.findByTenantIdAndId(tenant, order.getId()).orElseThrow();
          stored.setStatus(OrderStatus.CONFIRMED);
          return orderRows.saveAndFlush(stored);
        });
    var held = pick(openSources(order));
    var request = applyRequest(held);
    var plan = in(tenant, () -> cutSessions.apply(held.selectionId(), request, actor));
    assertThat(plan.state()).isEqualTo(StockUnitCutPlanState.PREPARED);
    assertThat(in(tenant, () -> cutSessions.apply(held.selectionId(), request, actor)))
        .isEqualTo(plan);
    var line = order.getLines().getFirst().getId();
    assertThat(in(tenant, () -> allocations.activeForLine(tenant, line))).hasSize(17);
    assertThat(in(tenant, () -> holds.heldPieceIds(line))).isEmpty();
    assertThat(in(tenant, () -> cutSessions.status(held.selectionId(), actor)).grants()).isEmpty();
  }

  @Test
  void provisionalAndAppliedDraftSourcesCannotBeConsumedThroughEitherLegacyEndpoint() {
    var held = pick(openSources(createExact()));
    assertConsumptionRefused();
    apply(held);
    assertConsumptionRefused();
    assertThat(in(tenant, () -> stockUnits.findById(rolls.get(16)).orElseThrow()).getStatus())
        .isEqualTo(StockUnitStatus.AVAILABLE);
  }

  @Test
  void recordedSourceCannotBeConsumedAsUnrelatedWorkOrderStock() {
    var order = createExact();
    var plan = apply(pick(openSources(order)));
    releaseForExecution(order);
    var recorded =
        command(
            command(plan, StockUnitCutPlanDtos.Action.APPROVE, null),
            StockUnitCutPlanDtos.Action.RECORD,
            measured());
    assertConsumptionRefused();
    assertThat(in(tenant, () -> cutPlans.get(recorded.planId(), actor)).state())
        .isEqualTo(StockUnitCutPlanState.RECORDED);
    assertThat(
            in(
                tenant,
                () -> allocations.activeForLine(tenant, order.getLines().getFirst().getId())))
        .hasSize(17);
    // Refused consumption has not damaged the source: verification still completes normally.
    assertThat(command(recorded, StockUnitCutPlanDtos.Action.VERIFY, null).state())
        .isEqualTo(StockUnitCutPlanState.COMPLETED);
  }

  @Test
  void cancellingRecordedSourcesAcrossLotsCancelsOnlyTheFullyReleasedReservation() {
    UUID cutLot = UUID.randomUUID();
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              """
          insert into production.production_execution_batch
          (id,tenant_id,uid,product_id,product_type,batch_code,quantity,reserved_quantity,
           consumed_quantity,waste_quantity,unit,status)
          values (?,?,?,?,'FABRIC',?,125,0,0,0,'M','AVAILABLE')
          """,
              cutLot,
              tenant,
              cutLot.toString(),
              product,
              "LOT-NORTHBRIDGE-" + cutLot);
          jdbc.update(
              "update production.stock_unit set batch_id=? where id=?", cutLot, rolls.get(16));
          jdbc.update(
              "update production.production_execution_batch set quantity=2875 where id=?", batch);
          return null;
        });
    var order = createExact();
    var plan = apply(pick(openSources(order)));
    releaseForExecution(order);
    var recorded =
        command(
            command(plan, StockUnitCutPlanDtos.Action.APPROVE, null),
            StockUnitCutPlanDtos.Action.RECORD,
            measured());
    in(tenant, () -> orders.cancelOrder(order.getId(), actor));
    assertThat(in(tenant, () -> cutPlans.get(recorded.planId(), actor)).state())
        .isEqualTo(StockUnitCutPlanState.DISPUTED);
    assertThat(
            in(
                tenant,
                () -> allocations.activeForLine(tenant, order.getLines().getFirst().getId())))
        .singleElement()
        .satisfies(row -> assertThat(row.stockUnitId()).isEqualTo(rolls.get(16)));
    system.executeInTransaction(
        jdbc -> {
          assertThat(
                  jdbc.queryForObject(
                      "select reserved_quantity from production.production_execution_batch where id=?",
                      BigDecimal.class,
                      batch))
              .isEqualByComparingTo("0");
          assertThat(
                  jdbc.queryForObject(
                      "select reserved_quantity from production.production_execution_batch where id=?",
                      BigDecimal.class,
                      cutLot))
              .isEqualByComparingTo("125");
          assertThat(
                  jdbc.queryForObject(
                      "select status from production.production_execution_batch_reservation where batch_id=? and reference_id=?",
                      String.class,
                      batch,
                      order.getLines().getFirst().getId()))
              .isEqualTo("CANCELLED");
          return null;
        });
  }

  private void assertConsumptionRefused() {
    assertThatThrownBy(() -> in(tenant, () -> stock.consume(rolls.get(16), BigDecimal.ONE)))
        .isInstanceOf(StockUnitHeldException.class);
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> stock.consumeReserved(rolls.get(16), BigDecimal.ONE, UUID.randomUUID())))
        .isInstanceOf(StockUnitHeldException.class);
    assertThat(
            in(tenant, () -> stockUnits.findById(rolls.get(16)).orElseThrow()).getCurrentWeight())
        .isEqualByComparingTo("30");
  }
}
