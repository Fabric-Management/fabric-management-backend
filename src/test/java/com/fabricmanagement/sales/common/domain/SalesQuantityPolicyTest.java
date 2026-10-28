package com.fabricmanagement.sales.common.domain;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SalesQuantityPolicyTest {
  final UUID actor = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-20T10:00:00Z");

  @Test
  void exactQuantityCannotBeInflatedOrReducedByWholeRollApply() {
    var line = order();
    line.recordQuantityPolicy(
        SalesQuantityPolicy.EXACT, actor, now, "Customer requires exactly 2,050 metres");
    for (String proposed : new String[] {"2125", "2000"}) {
      assertThatThrownBy(
              () -> line.applyStockQuantity(new BigDecimal("2050"), new BigDecimal(proposed)))
          .isInstanceOf(OrderIntakeException.class);
    }
    assertThat(line.getRequestedQty()).isEqualByComparingTo("2050");
    line.applyStockQuantity(new BigDecimal("2050.000"), new BigDecimal("2050.0"));
    assertThat(line.getRequestedQty()).isEqualByComparingTo("2050");
  }

  @Test
  void anAgreedWholeRollQuantityPreservesTheCustomerRequest() {
    var line = order();
    line.recordQuantityPolicy(
        SalesQuantityPolicy.BY_AGREEMENT, actor, now, "Customer will review the proposed rolls");
    line.applyStockQuantity(new BigDecimal("2050"), new BigDecimal("2125"));
    assertThat(line.getRequestedQty()).isEqualByComparingTo("2125");
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("2050");
    assertThat(line.getQuantityTerms().getRecordedBy()).isEqualTo(actor);
    assertThat(line.getQuantityTerms().getRecordedAt()).isEqualTo(now);
  }

  @Test
  void exactCannotRetroactivelyBlessAnAlreadyInflatedQuantity() {
    var line = order();
    line.applyStockQuantity(new BigDecimal("2050"), new BigDecimal("2125"));
    assertThatThrownBy(
            () ->
                line.recordQuantityPolicy(
                    SalesQuantityPolicy.EXACT, actor, now, "Customer refuses the extra quantity"))
        .isInstanceOf(OrderIntakeException.class);
    assertThat(line.getQuantityPolicy()).isEqualTo(SalesQuantityPolicy.UNRECORDED);
  }

  @Test
  void historicalQuoteDoesNotInventAnOriginalRequestOrExactDecision() {
    var line = new QuoteLine();
    line.setRequestedQty(new BigDecimal("2125"));
    assertThat(line.getCustomerRequestQty()).isNull();
    assertThat(line.getInitialRequestedQty()).isNull();
    assertThat(line.getQuantityPolicy()).isEqualTo(SalesQuantityPolicy.UNRECORDED);
    assertThatThrownBy(
            () ->
                line.recordQuantityPolicy(
                    SalesQuantityPolicy.EXACT, actor, now, "Exact quantity requested"))
        .isInstanceOf(OrderIntakeException.class);
  }

  @Test
  void quoteApplyKeepsFirstRequestAndCommercialQuantitySeparate() {
    var line = new QuoteLine();
    line.setRequestedQty(new BigDecimal("2050"));
    line.captureCustomerRequest(new BigDecimal("2050"));
    line.applyStockQuantity(new BigDecimal("2050"), new BigDecimal("2125"));
    line.updateEditableFields(new BigDecimal("2125.00"), "M", new BigDecimal("8.60"));
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("2050");
    assertThat(line.getInitialRequestedQty()).isEqualByComparingTo("2050");
    assertThat(line.lineTotal()).isEqualByComparingTo("18275.00");
  }

  @Test
  void aNewQuantityDecisionInvalidatesOldAcceptanceAndStockTerms() {
    var line = order();
    var accepted = com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms.fingerprint(line);
    var order =
        com.fabricmanagement.sales.salesorder.domain.SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .build();
    var selected =
        com.fabricmanagement.sales.orderintake.domain.StockSelectionTerms.fingerprint(order, line);
    line.recordQuantityPolicy(
        SalesQuantityPolicy.EXACT, actor, now, "Customer refuses extra metres");
    assertThat(com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms.fingerprint(line))
        .isNotEqualTo(accepted);
    assertThat(
            com.fabricmanagement.sales.orderintake.domain.StockSelectionTerms.fingerprint(
                order, line))
        .isNotEqualTo(selected);
  }

  @Test
  void changingAKnownDecisionNeedsAnActorAndReasonAndCannotEraseEvidence() {
    var terms = new SalesQuantityTerms();
    assertThatThrownBy(() -> terms.record(SalesQuantityPolicy.EXACT, null, now, "Exact requested"))
        .isInstanceOf(OrderIntakeException.class);
    assertThatThrownBy(() -> terms.record(SalesQuantityPolicy.EXACT, actor, now, " "))
        .isInstanceOf(OrderIntakeException.class);
    terms.record(SalesQuantityPolicy.EXACT, actor, now, "Exact requested");
    assertThatThrownBy(() -> terms.record(SalesQuantityPolicy.UNRECORDED, actor, now, "Erase"))
        .isInstanceOf(OrderIntakeException.class);
    var copied = terms.copy();
    copied.record(
        SalesQuantityPolicy.BY_AGREEMENT,
        actor,
        now.plusSeconds(1),
        "Customer accepts a proposed amount");
    assertThat(terms.getPolicy()).isEqualTo(SalesQuantityPolicy.EXACT);
    assertThat(copied.getPolicy()).isEqualTo(SalesQuantityPolicy.BY_AGREEMENT);
  }

  private SalesOrderLine order() {
    return SalesOrderLine.builder()
        .productId(UUID.randomUUID())
        .requestedQty(new BigDecimal("2050"))
        .customerRequestQty(new BigDecimal("2050"))
        .unit("M")
        .build();
  }
}
