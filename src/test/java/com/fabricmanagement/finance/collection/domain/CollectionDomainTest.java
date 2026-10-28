package com.fabricmanagement.finance.collection.domain;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.finance.collection.domain.CollectionTypes.*;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionDomainTest {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

  @Test
  void receivedAndPresentedChequesHaveNoClearingPayment() {
    var cheque = cheque();
    assertThat(cheque.getState()).isEqualTo(ChequeState.RECEIVED);
    assertThat(cheque.getClearedPaymentId()).isNull();
    cheque.transition(ChequeAction.PRESENT, null, "Bank deposit slip received");
    assertThat(cheque.getState()).isEqualTo(ChequeState.PRESENTED);
    assertThat(cheque.getClearedPaymentId()).isNull();
    assertThatThrownBy(() -> cheque.transition(ChequeAction.CLEAR, null, "Missing cash record"))
        .isInstanceOf(CollectionRuleException.class);
    assertThat(cheque.getState()).isEqualTo(ChequeState.PRESENTED);
  }

  @Test
  void clearingRequiresAPaymentAndTerminalInstrumentsCannotBeRecleared() {
    var cheque = cheque();
    UUID payment = UUID.randomUUID();
    cheque.transition(ChequeAction.CLEAR, payment, "Bank statement confirms clearing");
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    assertThat(cheque.getClearedPaymentId()).isEqualTo(payment);
    assertThatThrownBy(() -> cheque.transition(ChequeAction.CLEAR, payment, "Duplicate"))
        .isInstanceOf(CollectionRuleException.class);
    cheque.transition(ChequeAction.DISHONOUR, null, "Bank reversal notice");
    cheque.transition(
        ChequeAction.PRESENT, null, "Bank accepted the instrument for re-presentment");
    assertThat(cheque.getClearedPaymentId()).isNull();
    cheque.transition(ChequeAction.CLEAR, UUID.randomUUID(), "A new bank clearing was verified");
    cheque.transition(ChequeAction.DISHONOUR, null, "Second bank dishonour notice");
    cheque.transition(ChequeAction.RETURN, null, "Instrument returned to buyer");
    assertThat(cheque.getState().countsAsReceived()).isFalse();
    assertThatThrownBy(
            () ->
                cheque.transition(ChequeAction.CLEAR, UUID.randomUUID(), "No re-presentment proof"))
        .isInstanceOf(CollectionRuleException.class);
  }

  @Test
  void amountsRespectCurrencyPrecisionAndPartialCashNeverBecomesAFloatingPointTotal() {
    assertThat(CollectionAmounts.positive(new BigDecimal("33.34"), "GBP"))
        .isEqualByComparingTo("33.34");
    assertThatThrownBy(() -> CollectionAmounts.positive(new BigDecimal("0.001"), "GBP"))
        .isInstanceOf(CollectionRuleException.class);
    assertThatThrownBy(() -> CollectionAmounts.positive(BigDecimal.ZERO, "GBP"))
        .isInstanceOf(CollectionRuleException.class);
    assertThat(CollectionAmounts.outstanding(new BigDecimal("4284.00"), new BigDecimal("1000.00")))
        .isEqualByComparingTo("3284.00");
  }

  @Test
  void cancelledAndOvermatchedExpectationsRejectNewCashWithoutErasingPriorEvidence() {
    var row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "GBP");
    row.revise(
        UUID.randomUUID(),
        new BigDecimal("4284.00"),
        Kind.INSTALMENT,
        Method.CASH,
        DATE,
        false,
        null,
        NOW,
        false);
    row.requireMatchable(new BigDecimal("1000.00"), new BigDecimal("3284.00"));
    assertThatThrownBy(
            () -> row.requireMatchable(new BigDecimal("1000.00"), new BigDecimal("4000.00")))
        .isInstanceOf(CollectionRuleException.class);
    row.cancel();
    assertThat(row.getAmount()).isEqualByComparingTo("4284.00");
    assertThatThrownBy(() -> row.requireMatchable(BigDecimal.ZERO, BigDecimal.ONE))
        .isInstanceOf(CollectionRuleException.class);
  }

  @Test
  void receiptReplayIsBoundToActorAndCanonicalBody() {
    UUID actor = UUID.randomUUID();
    var receipt =
        CollectionCommandReceipt.create(
            UUID.randomUUID(),
            actor,
            "hash",
            "OBLIGATION",
            UUID.randomUUID(),
            1,
            null,
            java.util.List.of());
    receipt.requireReplay(actor, "hash");
    assertThatThrownBy(() -> receipt.requireReplay(actor, "different"))
        .isInstanceOf(CollectionRuleException.class);
    assertThatThrownBy(() -> receipt.requireReplay(UUID.randomUUID(), "hash"))
        .isInstanceOf(CollectionRuleException.class);
  }

  @Test
  void reversalKeepsOriginalOccurrenceAndPaymentEvidence() {
    UUID actor = UUID.randomUUID();
    var match =
        CollectionPaymentMatch.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            new BigDecimal("1000.00"),
            DATE,
            NOW,
            actor,
            "Bank receipt verified");
    match.reverse(
        DATE.plusDays(1),
        NOW.plusSeconds(86400),
        NOW.plusSeconds(90000),
        actor,
        "Bank transfer was assigned to the wrong instalment");
    assertThat(match.getAmount()).isEqualByComparingTo("1000.00");
    assertThat(match.getOccurredAt()).isEqualTo(NOW);
    assertThat(match.getReversedOn()).isEqualTo(DATE.plusDays(1));
    assertThatThrownBy(() -> match.reverse(DATE.plusDays(1), NOW, NOW, actor, "Second reversal"))
        .isInstanceOf(CollectionRuleException.class);
  }

  private CollectionCheque cheque() {
    return CollectionCheque.receive(
        UUID.randomUUID(),
        "GBP",
        new BigDecimal("4284.00"),
        DATE,
        DATE.plusMonths(3),
        "NORTHBANK-0042",
        "Signed instrument scan");
  }
}
