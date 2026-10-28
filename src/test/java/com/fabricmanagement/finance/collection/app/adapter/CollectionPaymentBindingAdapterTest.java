package com.fabricmanagement.finance.collection.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.CollectionCommandStore;
import com.fabricmanagement.finance.collection.app.CollectionEvents;
import com.fabricmanagement.finance.collection.app.CollectionMatchReversal;
import com.fabricmanagement.finance.collection.app.CollectionRefundLedger;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionPaymentMatch;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class CollectionPaymentBindingAdapterTest {
  final UUID tenant = UUID.randomUUID(), payment = UUID.randomUUID(), actor = UUID.randomUUID();
  final LocalDate on = LocalDate.of(2026, 10, 20);
  final Instant now = Instant.parse("2026-10-20T12:00:00Z");
  CollectionPaymentMatchRepository matches;
  CollectionChequeRepository cheques;
  CollectionMatchReversal reversal;
  CollectionCommandStore commands;
  CollectionRefundLedger refunds;
  CollectionObligationRepository obligations;
  CollectionEvents events;
  PaymentRepository payments;
  CollectionPaymentBindingAdapter adapter;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    matches = mock(CollectionPaymentMatchRepository.class);
    cheques = mock(CollectionChequeRepository.class);
    reversal = mock(CollectionMatchReversal.class);
    commands = mock(CollectionCommandStore.class);
    when(commands.now()).thenReturn(now);
    refunds = mock(CollectionRefundLedger.class);
    obligations = mock(CollectionObligationRepository.class);
    events = mock(CollectionEvents.class);
    payments = mock(PaymentRepository.class);
    adapter =
        new CollectionPaymentBindingAdapter(
            matches, cheques, reversal, commands, refunds, obligations, events, payments);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void aPaymentWithoutAChequeClearingIsNotBoundToAnInstrument() {
    assertThat(adapter.isBoundToInstrument(tenant, payment)).isFalse();
  }

  @Test
  void aChequeClearingBindsThePaymentToItsInstrument() {
    when(cheques.existsByTenantIdAndClearedPaymentId(tenant, payment)).thenReturn(true);
    assertThat(adapter.isBoundToInstrument(tenant, payment)).isTrue();
  }

  @Test
  void everyActiveMatchIsReversedAsOfTheGivenDateAndNothingIsDeleted() {
    var first = mock(CollectionPaymentMatch.class);
    var second = mock(CollectionPaymentMatch.class);
    when(matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenant, payment))
        .thenReturn(List.of(first, second));

    int released = adapter.releaseMatches(tenant, payment, on, actor, "Paid twice");

    assertThat(released).isEqualTo(2);
    InOrder order = inOrder(reversal);
    order.verify(reversal).reverse(first, on, now, actor, "Paid twice");
    order.verify(reversal).reverse(second, on, now, actor, "Paid twice");
    verify(matches, never()).delete(any());
  }

  @Test
  void aPaymentWithNoActiveMatchesReleasesNothing() {
    when(matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenant, payment))
        .thenReturn(List.of());

    assertThat(adapter.releaseMatches(tenant, payment, on, actor, "Paid twice")).isZero();
    verifyNoInteractions(reversal);
  }

  @Test
  void whatIsSetAsideForARefundComesFromTheRefundLedger() {
    when(refunds.held(tenant, payment)).thenReturn(new BigDecimal("40.00"));

    assertThat(adapter.refundHeld(tenant, payment)).isEqualByComparingTo("40.00");
    assertThat(adapter.holdsRefund(tenant, payment)).isTrue();
  }

  @Test
  void whatWasAlreadyPaidBackComesFromTheRefundLedger() {
    when(refunds.paid(tenant, payment)).thenReturn(new BigDecimal("25.00"));

    assertThat(adapter.refundPaid(tenant, payment)).isEqualByComparingTo("25.00");
  }

  @Test
  void aRefundIsNotOwedAgainWhenThePaymentItWasPaidFromWasReversedOrVoided() {
    UUID reversed = UUID.randomUUID(), voided = UUID.randomUUID(), standing = UUID.randomUUID();
    UUID unknown = UUID.randomUUID();
    // Built first: stubbing a mock while another stubbing is half written is refused.
    var reversedReceipt = receipt(true, PaymentStatus.RECEIVED);
    var voidedReceipt = receipt(false, PaymentStatus.VOIDED);
    var standingReceipt = receipt(false, PaymentStatus.RECEIVED);
    when(payments.findByTenantIdAndId(tenant, reversed)).thenReturn(Optional.of(reversedReceipt));
    when(payments.findByTenantIdAndId(tenant, voided)).thenReturn(Optional.of(voidedReceipt));
    when(payments.findByTenantIdAndId(tenant, standing)).thenReturn(Optional.of(standingReceipt));
    when(payments.findByTenantIdAndId(tenant, unknown)).thenReturn(Optional.empty());

    adapter.reopenRefunds(tenant, payment, on, actor, "Refund payment reversed");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Predicate<UUID>> taken =
        ArgumentCaptor.forClass((Class<Predicate<UUID>>) (Class<?>) Predicate.class);
    verify(refunds)
        .reopenSettledBy(
            eq(tenant), eq(payment), eq(on), eq(now), eq(actor), any(), taken.capture());
    assertThat(taken.getValue().test(reversed)).isTrue();
    assertThat(taken.getValue().test(voided)).isTrue();
    assertThat(taken.getValue().test(standing)).isFalse();
    assertThat(taken.getValue().test(unknown)).isFalse();
  }

  private static Payment receipt(boolean reversed, PaymentStatus status) {
    var value = mock(Payment.class);
    when(value.isReversed()).thenReturn(reversed);
    when(value.getStatus()).thenReturn(status);
    return value;
  }

  @Test
  void aPaymentWithNothingSetAsideHoldsNoRefund() {
    when(refunds.held(tenant, payment)).thenReturn(BigDecimal.ZERO);

    assertThat(adapter.holdsRefund(tenant, payment)).isFalse();
  }

  @Test
  void reopeningRefundsTellsEachAffectedObligationItChangedOnce() {
    UUID first = UUID.randomUUID();
    var row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), actor, "GBP");
    row.setId(first);
    long before = row.getFinancialRevision();
    when(refunds.reopenSettledBy(
            eq(tenant), eq(payment), eq(on), eq(now), eq(actor), eq("Payment reversed"), any()))
        .thenReturn(List.of(first, first));
    when(obligations.findByTenantIdAndId(tenant, first)).thenReturn(Optional.of(row));

    int reopened = adapter.reopenRefunds(tenant, payment, on, actor, "Payment reversed");

    assertThat(reopened).isEqualTo(2);
    assertThat(row.getFinancialRevision()).isEqualTo(before + 1);
    verify(obligations).saveAndFlush(row);
    verify(events).changed(first);
  }

  @Test
  void aPaymentThatPaidNoRefundReopensNothing() {
    when(refunds.reopenSettledBy(
            eq(tenant), eq(payment), eq(on), eq(now), eq(actor), eq("Voided"), any()))
        .thenReturn(List.of());

    assertThat(adapter.reopenRefunds(tenant, payment, on, actor, "Voided")).isZero();
    verifyNoInteractions(events);
  }

  @Test
  void aPaymentWithAnActiveMatchHasMatches() {
    when(matches.existsByTenantIdAndPaymentIdAndReversedAtIsNull(tenant, payment)).thenReturn(true);

    assertThat(adapter.hasActiveMatches(tenant, payment)).isTrue();
    assertThat(adapter.hasActiveMatches(tenant, UUID.randomUUID())).isFalse();
  }

  @Test
  void aPaymentSettlesARefundOnlyWhileAtLeastOneRefundIsPaidByIt() {
    when(refunds.settledBy(tenant, payment))
        .thenReturn(List.of(mock(CollectionRefundLedger.View.class)));

    assertThat(adapter.settlesRefund(tenant, payment)).isTrue();
    assertThat(adapter.settlesRefund(tenant, UUID.randomUUID())).isFalse();
  }

  @Test
  void cancellingUnpaidRefundsTellsEachAffectedObligationItChangedOnce() {
    UUID first = UUID.randomUUID();
    var row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), actor, "GBP");
    row.setId(first);
    long before = row.getFinancialRevision();
    when(refunds.cancelUnpaidOf(tenant, payment, on, now, actor, "Cheque returned"))
        .thenReturn(List.of(first, first));
    when(obligations.findByTenantIdAndId(tenant, first)).thenReturn(Optional.of(row));

    int cancelled = adapter.cancelUnpaidRefunds(tenant, payment, on, actor, "Cheque returned");

    assertThat(cancelled).isEqualTo(2);
    assertThat(row.getFinancialRevision()).isEqualTo(before + 1);
    verify(obligations).saveAndFlush(row);
    verify(events).changed(first);
  }

  @Test
  void aPaymentHoldingNoUnpaidRefundCancelsNothing() {
    when(refunds.cancelUnpaidOf(tenant, payment, on, now, actor, "Returned")).thenReturn(List.of());

    assertThat(adapter.cancelUnpaidRefunds(tenant, payment, on, actor, "Returned")).isZero();
    verifyNoInteractions(events);
  }

  @Test
  void anotherTenantIsRefusedInsteadOfAnsweredFreeOrReleased() {
    UUID other = UUID.randomUUID();
    assertThatThrownBy(() -> adapter.isBoundToInstrument(other, payment))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> adapter.releaseMatches(other, payment, on, actor, "x"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> adapter.refundHeld(other, payment))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> adapter.reopenRefunds(other, payment, on, actor, "x"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> adapter.hasActiveMatches(other, payment))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> adapter.settlesRefund(other, payment))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> adapter.cancelUnpaidRefunds(other, payment, on, actor, "x"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> adapter.assertMatchesReleasableOn(other, payment, on))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> adapter.assertRefundsReleasableOn(other, payment, on))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(cheques, reversal, refunds, events);
  }

  private CollectionPaymentMatch matchedOn(LocalDate date) {
    var match = mock(CollectionPaymentMatch.class);
    when(match.getEffectiveDate()).thenReturn(date);
    return match;
  }

  @Test
  void aReleaseDatedBeforeTheLatestActiveMatchIsRefusedWithTheFirstDayThatWorks() {
    var active = List.of(matchedOn(on.minusDays(9)), matchedOn(on.plusDays(2)));
    when(matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenant, payment)).thenReturn(active);

    assertThatThrownBy(() -> adapter.assertMatchesReleasableOn(tenant, payment, on))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused -> {
              assertThat(refused.getErrorCode()).isEqualTo("COLLECTION_DATE_BEFORE_MATCH");
              assertThat(refused.getDetails())
                  .containsEntry("earliestDate", on.plusDays(2).toString());
              assertThat(refused.getArgs()).containsExactly(on.plusDays(2).toString());
            });
    // Checking only reads: nothing was reversed on the way to refusing.
    verifyNoInteractions(reversal);
  }

  @Test
  void aReleaseOnOrAfterEveryActiveMatchPassesTheCheck() {
    var active = List.of(matchedOn(on.minusDays(3)), matchedOn(on));
    when(matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenant, payment)).thenReturn(active);

    adapter.assertMatchesReleasableOn(tenant, payment, on);
    adapter.assertMatchesReleasableOn(tenant, payment, on.plusDays(1));
  }

  @Test
  void aReleaseDatedBeforeTheHistoryOfARefundItWouldMoveIsRefusedWithTheDayThatWorks() {
    when(refunds.releaseFloor(tenant, payment)).thenReturn(on.plusDays(4));

    assertThatThrownBy(() -> adapter.assertRefundsReleasableOn(tenant, payment, on))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused -> {
              assertThat(refused.getErrorCode()).isEqualTo("REFUND_DATE_BEFORE_HISTORY");
              assertThat(refused.getDetails())
                  .containsEntry("earliestDate", on.plusDays(4).toString());
              assertThat(refused.getArgs()).containsExactly(on.plusDays(4).toString());
            });
    verify(refunds, never()).reopenSettledBy(any(), any(), any(), any(), any(), any(), any());
    verify(refunds, never()).cancelUnpaidOf(any(), any(), any(), any(), any(), any());
  }

  @Test
  void aReleaseOnOrAfterTheHistoryOfEveryRefundItMovesPassesTheCheck() {
    when(refunds.releaseFloor(tenant, payment)).thenReturn(on);

    adapter.assertRefundsReleasableOn(tenant, payment, on);
    adapter.assertRefundsReleasableOn(tenant, payment, on.plusDays(1));
  }

  @Test
  void aPaymentThatMovesNoRefundCanBeReleasedOnAnyDate() {
    when(refunds.releaseFloor(tenant, payment)).thenReturn(null);

    adapter.assertRefundsReleasableOn(tenant, payment, on.minusYears(1));
  }

  @Test
  void aPaymentWithNoActiveMatchesCanBeReleasedOnAnyDate() {
    when(matches.findByTenantIdAndPaymentIdAndReversedAtIsNull(tenant, payment))
        .thenReturn(List.of());

    adapter.assertMatchesReleasableOn(tenant, payment, on.minusYears(1));
  }
}
