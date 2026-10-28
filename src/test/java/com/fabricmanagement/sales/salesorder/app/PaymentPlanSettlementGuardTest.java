package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.finance.collection.app.port.CollectionSettlementPort;
import com.fabricmanagement.sales.salesorder.app.PaymentPlanSettlementGuard.Shortfall;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentPlanSettlementGuardTest {
  final UUID tenant = UUID.randomUUID(), order = UUID.randomUUID();
  final UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();

  static PaymentPlanContent.Line line(UUID obligation, int ordinal, String amount) {
    return new PaymentPlanContent.Line(
        obligation,
        ordinal,
        Kind.INSTALMENT,
        amount == null ? null : new BigDecimal(amount),
        null,
        null,
        null,
        null,
        false);
  }

  static PaymentPlanContent.CurrencyGroup group(String currency, PaymentPlanContent.Line... rows) {
    return new PaymentPlanContent.CurrencyGroup(currency, null, null, List.of(rows));
  }

  static PaymentPlanContent plan(PaymentPlanContent.CurrencyGroup... groups) {
    return new PaymentPlanContent(UUID.randomUUID(), 1, "a".repeat(64), List.of(groups), List.of());
  }

  @Test
  void aLineLoweredBelowTheCashRecordedAgainstItIsAShortfall() {
    var agreed = plan(group("GBP", line(first, 1, "100.00"), line(second, 2, "100.00")));
    var proposed = plan(group("GBP", line(first, 1, "60.00"), line(second, 2, "100.00")));
    var found =
        PaymentPlanSettlementGuard.shortfalls(
            agreed, proposed, Map.of(first, new BigDecimal("80")));
    assertThat(found).hasSize(1);
    assertThat(found.get(0).obligationId()).isEqualTo(first);
    assertThat(found.get(0).currency()).isEqualTo("GBP");
    assertThat(found.get(0).settled()).isEqualByComparingTo("80");
    assertThat(found.get(0).agreed()).isEqualByComparingTo("100");
    assertThat(found.get(0).proposed()).isEqualByComparingTo("60");
  }

  @Test
  void aLineLoweredButStillCoveringTheCashIsNotAShortfall() {
    var agreed = plan(group("GBP", line(first, 1, "100.00")));
    var proposed = plan(group("GBP", line(first, 1, "60.00")));
    assertThat(
            PaymentPlanSettlementGuard.shortfalls(
                agreed, proposed, Map.of(first, new BigDecimal("60.00"))))
        .isEmpty();
    assertThat(
            PaymentPlanSettlementGuard.shortfalls(
                agreed, proposed, Map.of(first, new BigDecimal("40"))))
        .isEmpty();
  }

  @Test
  void aLineTheProposalLeavesOutIsReducedToNothing() {
    var agreed = plan(group("GBP", line(first, 1, "100.00"), line(second, 2, "100.00")));
    var proposed = plan(group("GBP", line(second, 1, "100.00")));
    var found =
        PaymentPlanSettlementGuard.shortfalls(agreed, proposed, Map.of(first, new BigDecimal("1")));
    assertThat(found).extracting(Shortfall::obligationId).containsExactly(first);
    assertThat(found.get(0).proposed()).isEqualByComparingTo("0");
  }

  @Test
  void raisingOrKeepingALineIsNeverAShortfallEvenWhenItIsAlreadyOverCollected() {
    var agreed = plan(group("GBP", line(first, 1, "100.00"), line(second, 2, "100.00")));
    var kept = plan(group("GBP", line(first, 1, "100.00"), line(second, 2, "130.00")));
    // Over-collected before this revision: the report keeps flagging it, the submit is not new.
    var settled = Map.of(first, new BigDecimal("120"), second, new BigDecimal("110"));
    assertThat(PaymentPlanSettlementGuard.shortfalls(agreed, kept, settled)).isEmpty();
  }

  @Test
  void loweringAnAlreadyOverCollectedLineFurtherIsAShortfall() {
    var agreed = plan(group("GBP", line(first, 1, "100.00")));
    var proposed = plan(group("GBP", line(first, 1, "90.00")));
    assertThat(
            PaymentPlanSettlementGuard.shortfalls(
                agreed, proposed, Map.of(first, new BigDecimal("120"))))
        .hasSize(1);
  }

  @Test
  void identitiesTheProposalInventsAreIgnoredAndEachCurrencyIsReportedUnderItsOwnCode() {
    var agreed =
        plan(group("GBP", line(first, 1, "100.00")), group("USD", line(second, 1, "50.00")));
    var proposed =
        plan(
            group("GBP", line(first, 1, "100.00"), line(third, 2, "10.00")),
            group("USD", line(second, 1, "20.00")));
    var found =
        PaymentPlanSettlementGuard.shortfalls(
            agreed, proposed, Map.of(first, new BigDecimal("100"), second, new BigDecimal("30")));
    assertThat(found).hasSize(1);
    assertThat(found.get(0).obligationId()).isEqualTo(second);
    assertThat(found.get(0).currency()).isEqualTo("USD");
  }

  @Test
  void nothingCollectedMeansNothingToCheck() {
    var agreed = plan(group("GBP", line(first, 1, "100.00")));
    var proposed = plan(group("GBP", line(first, 1, "1.00")));
    assertThat(PaymentPlanSettlementGuard.shortfalls(agreed, proposed, Map.of())).isEmpty();
  }

  @Test
  void aMissingAmountOrIdentityNeverBreaksTheCheck() {
    var agreed = plan(group("GBP", line(first, 1, null), line(null, 2, "10.00")));
    var proposed = plan(group("GBP", line(first, 1, null), line(null, 2, null)));
    assertThat(
            PaymentPlanSettlementGuard.shortfalls(
                agreed, proposed, Map.of(first, new BigDecimal("5"))))
        .isEmpty();
  }

  @Test
  void aFirstPlanDoesNotAskFinanceAndAnAmendmentAsksOnceForThisOrder() {
    var port = mock(CollectionSettlementPort.class);
    var guard = new PaymentPlanSettlementGuard(port);
    var proposed = plan(group("GBP", line(first, 1, "10.00")));
    assertThat(guard.shortfalls(tenant, order, null, proposed)).isEmpty();
    verifyNoInteractions(port);
    var agreed = plan(group("GBP", line(first, 1, "100.00")));
    when(port.settledByObligation(tenant, order)).thenReturn(Map.of(first, new BigDecimal("50")));
    assertThat(guard.shortfalls(tenant, order, agreed, proposed)).hasSize(1);
    verify(port).settledByObligation(tenant, order);
  }
}
