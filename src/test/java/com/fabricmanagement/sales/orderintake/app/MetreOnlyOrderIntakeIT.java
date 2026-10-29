package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.orderintake.dto.SalesStockPlanningDto;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import org.junit.jupiter.api.Test;

class MetreOnlyOrderIntakeIT extends MetreOrderITSupport {
  @Test
  void exactOrderCanBeCreatedAndReplayedWithoutChoosingOrHoldingAnyRoll() {
    var request = metreOrder(SalesQuantityPolicy.EXACT);
    var saved = in(tenant, () -> orders.createOrder(request));
    var line = saved.getLines().getFirst();
    assertThat(line.getRequestedQty()).isEqualByComparingTo("2050");
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("2050");
    assertThat(line.getQuantityTerms().policy()).isEqualTo(SalesQuantityPolicy.EXACT);
    var work = in(tenant, () -> planning.order(saved.getId(), line.getId(), actor));
    assertThat(work.assignment()).isEqualTo(SalesStockPlanningDto.Assignment.UNASSIGNED);
    assertThat(work.heldPieceIds()).isEmpty();
    assertThat(work.cutAssessment())
        .isEqualTo(SalesStockPlanningDto.CutAssessment.EXACT_QUANTITY_REVIEW_REQUIRED);
    assertThat(work.capabilities())
        .anyMatch(
            action ->
                action.action() == SalesStockPlanningDto.Action.APPLY_CUT_PLAN && action.allowed());
    assertThat(in(tenant, () -> orders.createOrder(request)).getId()).isEqualTo(saved.getId());
    assertThatThrownBy(
            () -> in(otherTenant, () -> planning.order(saved.getId(), line.getId(), actor)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void unknownMeasurementsDoNotPreventMetreEntryOrBecomePhysicalReservation() {
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "update production.stock_unit set length=null, length_unit=null where batch_id=?",
              batch);
          return null;
        });
    var saved = in(tenant, () -> orders.createOrder(metreOrder(null)));
    var work =
        in(tenant, () -> planning.order(saved.getId(), saved.getLines().getFirst().getId(), actor));
    assertThat(work.assignment()).isEqualTo(SalesStockPlanningDto.Assignment.UNASSIGNED);
    assertThat(work.quantityTerms().policy()).isEqualTo(SalesQuantityPolicy.UNRECORDED);
    assertThat(work.heldPieceIds()).isEmpty();
  }

  @Test
  void quantityOnlyQuoteConversionKeepsTheKnownRequestAndExactDecision() {
    in(
        tenant,
        () -> {
          var quote = quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, quoteId).orElseThrow();
          quote
              .getLines()
              .getFirst()
              .recordQuantityPolicy(
                  SalesQuantityPolicy.EXACT,
                  actor,
                  java.time.Instant.now(),
                  "Customer requests exactly 2,050 metres");
          quote.setStatus(QuoteStatus.APPROVED);
          quotes.saveAndFlush(quote);
          return null;
        });
    var request = metreOrder(null);
    request.setQuoteId(quoteId);
    request.getLines().getFirst().setSourceQuoteLineId(quoteLineId);
    var saved = in(tenant, () -> orders.createOrder(request));
    var line = saved.getLines().getFirst();
    assertThat(line.getQuantityTerms().policy()).isEqualTo(SalesQuantityPolicy.EXACT);
    assertThat(line.getInitialRequestedQty()).isEqualByComparingTo("2050");
    assertThat(in(tenant, () -> planning.order(saved.getId(), line.getId(), actor)).heldPieceIds())
        .isEmpty();
  }
}
