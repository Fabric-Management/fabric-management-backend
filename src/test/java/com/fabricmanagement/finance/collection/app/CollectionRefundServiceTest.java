package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionRefund;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundAction;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundState;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests.CancelRefund;
import com.fabricmanagement.finance.collection.dto.CollectionRequests.SettleRefund;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentMethod;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectionRefundServiceTest {
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), partner = UUID.randomUUID();
  final UUID refundId = UUID.randomUUID(), outboundId = UUID.randomUUID();
  final LocalDate opened = LocalDate.of(2026, 10, 10), paidOn = LocalDate.of(2026, 10, 12);
  final LocalDate on = LocalDate.of(2026, 10, 20);
  final Instant now = Instant.parse("2026-10-20T12:00:00Z");
  CollectionCommandStore commands;
  CollectionPostingService posting;
  CollectionObligationRepository obligations;
  CollectionRefundLedger refunds;
  CollectionEvents events;
  FinancialPeriodGuard periods;
  CollectionRefundService service;
  CollectionObligation row;
  CollectionRefund refund;
  CollectionRefundLedger.View view;
  Payment outbound;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    commands = mock(CollectionCommandStore.class);
    posting = mock(CollectionPostingService.class);
    obligations = mock(CollectionObligationRepository.class);
    refunds = mock(CollectionRefundLedger.class);
    events = mock(CollectionEvents.class);
    periods = mock(FinancialPeriodGuard.class);
    service = new CollectionRefundService(commands, posting, obligations, refunds, events, periods);
    row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), partner, "GBP");
    row.setId(UUID.randomUUID());
    row.revise(
        UUID.randomUUID(),
        new BigDecimal("1000.00"),
        Kind.INSTALMENT,
        Method.BANK_TRANSFER,
        on,
        false,
        null,
        now,
        false);
    refund =
        CollectionRefund.open(
            row,
            UUID.randomUUID(),
            new BigDecimal("200.00"),
            opened,
            now,
            actor,
            "Overpaid",
            List.of());
    refund.setId(refundId);
    view = new CollectionRefundLedger.View(refund, RefundState.OPEN, 0, opened, null);
    outbound =
        Payment.builder()
            .tradingPartnerId(partner)
            .paymentNumber("PAY-OUT-1")
            .direction(PaymentDirection.OUTBOUND)
            .method(PaymentMethod.BANK_TRANSFER)
            .amount(Money.of(new BigDecimal("200.00"), "GBP"))
            .paymentDate(paidOn)
            .build();
    outbound.setId(outboundId);
    when(refunds.view(tenant, refundId)).thenReturn(view);
    when(posting.writable(row.getId(), actor)).thenReturn(row);
    when(posting.reversible(row.getId(), actor)).thenReturn(row);
    when(posting.payment(outboundId)).thenReturn(outbound);
    when(refunds.settledBy(tenant, outboundId)).thenReturn(List.of());
    when(commands.hash(any(), any(), any())).thenReturn("hash");
    when(commands.replay(any(), any(), any())).thenReturn(Optional.empty());
    when(posting.receipt(any(), any(), any(), any(), any(), any()))
        .thenReturn(mock(CollectionCommandReceipt.class));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  SettleRefund settle() {
    return new SettleRefund(UUID.randomUUID(), 0L, outboundId, now, "Refund sent");
  }

  CancelRefund cancel() {
    return new CancelRefund(UUID.randomUUID(), 0L, on, now, "Customer keeps it on account");
  }

  private static void assertRefused(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            CollectionRuleException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
  }

  @Test
  void anOutboundPaymentOfExactlyTheAmountSettlesTheRefundOnTheDayItWasPaid() {
    service.settle(refundId, settle(), actor);

    verify(refunds)
        .append(view, RefundAction.SETTLE, paidOn, now, actor, "Refund sent", outboundId);
    verify(periods).assertPostingAllowed(tenant, paidOn);
    verify(commands).time(paidOn, now);
    verify(events).changed(row.getId());
    verify(posting).receipt(any(), eq(actor), eq("hash"), eq(row), eq(outboundId), eq(List.of()));
  }

  /**
   * The event keeps the payment's own date, so voiding that payment later can reopen the refund on
   * a day inside its history. A payment dated before the refund was owed is refused, with the day
   * that works; the event is never moved away from the payment's date.
   */
  @Test
  void aPaymentDatedBeforeTheRefundWasOwedCannotPayItAndTheErrorNamesTheDayThatWorks() {
    view = new CollectionRefundLedger.View(refund, RefundState.OPEN, 2, on, null);
    when(refunds.view(tenant, refundId)).thenReturn(view);
    var request = new SettleRefund(UUID.randomUUID(), 2L, outboundId, now, "Refund sent");

    assertThatThrownBy(() -> service.settle(refundId, request, actor))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused -> {
              assertThat(refused.getErrorCode()).isEqualTo("REFUND_DATE_BEFORE_HISTORY");
              assertThat(refused.getDetails()).containsEntry("earliestDate", on.toString());
              assertThat(refused.getArgs()).containsExactly(on.toString());
            });
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(events);
  }

  @Test
  void aPaymentDatedOnTheLastDayOfTheRefundsHistoryMayPayIt() {
    view = new CollectionRefundLedger.View(refund, RefundState.OPEN, 2, paidOn, null);
    when(refunds.view(tenant, refundId)).thenReturn(view);
    var request = new SettleRefund(UUID.randomUUID(), 2L, outboundId, now, "Refund sent");

    service.settle(refundId, request, actor);

    verify(refunds)
        .append(view, RefundAction.SETTLE, paidOn, now, actor, "Refund sent", outboundId);
  }

  @Test
  void aStaleStepIsRefusedBeforeTheRefundIsTouched() {
    doThrow(CollectionRuleException.conflict("COLLECTION_VERSION_CONFLICT", "x"))
        .when(commands)
        .version(any(), any());

    assertRefused(() -> service.settle(refundId, settle(), actor), "COLLECTION_VERSION_CONFLICT");
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void theStepTheCallerMustEchoIsTheNumberOfEventsSoFar() {
    view = new CollectionRefundLedger.View(refund, RefundState.OPEN, 3, on, null);
    when(refunds.view(tenant, refundId)).thenReturn(view);

    service.cancel(refundId, cancel(), actor);

    verify(commands).version(0L, 3L);
  }

  @Test
  void aPaymentThatPaysADifferentAmountPartnerOrCurrencyOrDirectionIsRefused() {
    outbound.setAmount(Money.of(new BigDecimal("199.99"), "GBP"));
    assertRefused(() -> service.settle(refundId, settle(), actor), "REFUND_PAYMENT_INCOMPATIBLE");
    outbound.setAmount(Money.of(new BigDecimal("200.00"), "EUR"));
    assertRefused(() -> service.settle(refundId, settle(), actor), "REFUND_PAYMENT_INCOMPATIBLE");
    outbound.setAmount(Money.of(new BigDecimal("200.00"), "GBP"));
    outbound.setTradingPartnerId(UUID.randomUUID());
    assertRefused(() -> service.settle(refundId, settle(), actor), "REFUND_PAYMENT_INCOMPATIBLE");
    outbound.setTradingPartnerId(partner);
    outbound.setDirection(PaymentDirection.INBOUND);
    assertRefused(() -> service.settle(refundId, settle(), actor), "REFUND_PAYMENT_INCOMPATIBLE");
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void aVoidedReversedOrReversalPaymentCannotPayARefund() {
    outbound.voidPayment();
    assertRefused(() -> service.settle(refundId, settle(), actor), "REFUND_PAYMENT_INCOMPATIBLE");
  }

  @Test
  void aPaymentThatAlreadyPaysAnotherRefundCannotPayThisOneToo() {
    when(refunds.settledBy(tenant, outboundId))
        .thenReturn(
            List.of(
                new CollectionRefundLedger.View(
                    refund, RefundState.SETTLED, 1, paidOn, outboundId)));

    assertRefused(() -> service.settle(refundId, settle(), actor), "REFUND_PAYMENT_ALREADY_USED");
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void aPaymentAlreadyAllocatedToInvoicesCannotAlsoPayARefund() {
    outbound.allocate(
        UUID.randomUUID(),
        Money.of(new BigDecimal("50.00"), "GBP"),
        Money.of(new BigDecimal("100.00"), "GBP"),
        "GBP");

    assertRefused(() -> service.settle(refundId, settle(), actor), "REFUND_PAYMENT_ALLOCATED");
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(events);
  }

  @Test
  void aClosedPeriodStopsSettlementBeforeAnythingIsRecorded() {
    doThrow(new ClosedFinancialPeriodException(paidOn, YearMonth.from(paidOn)))
        .when(periods)
        .assertPostingAllowed(tenant, paidOn);

    assertThatThrownBy(() -> service.settle(refundId, settle(), actor))
        .isInstanceOf(ClosedFinancialPeriodException.class);
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(events);
  }

  @Test
  void anUnknownRefundIsNotFound() {
    UUID other = UUID.randomUUID();
    when(refunds.view(tenant, other)).thenReturn(null);

    assertThatThrownBy(() -> service.settle(other, settle(), actor))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.cancel(other, cancel(), actor))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void aRefundOfAnExpectationTheCallerCannotWorkIsNotFoundEither() {
    when(posting.writable(row.getId(), actor)).thenThrow(new NotFoundException("hidden"));

    assertThatThrownBy(() -> service.settle(refundId, settle(), actor))
        .isInstanceOf(NotFoundException.class);
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void cancellingNeedsTheReverseGrantOverTheExpectationNotMerelyTheWriteGrant() {
    when(posting.reversible(row.getId(), actor)).thenThrow(new NotFoundException("hidden"));

    assertThatThrownBy(() -> service.cancel(refundId, cancel(), actor))
        .isInstanceOf(NotFoundException.class);
    verify(posting, never()).writable(any(), any());
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(events);
  }

  @Test
  void aReplayedSettlementReturnsItsReceiptAndRecordsNothingAgain() {
    var receipt = mock(CollectionCommandReceipt.class);
    when(commands.replay(any(), eq(actor), eq("hash"))).thenReturn(Optional.of(receipt));

    assertThat(service.settle(refundId, settle(), actor)).isSameAs(receipt);
    verify(refunds, never()).append(any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(events);
  }

  @Test
  void cancellingStopsOwingTheMoneyAndLeavesItOnAccount() {
    service.cancel(refundId, cancel(), actor);

    verify(refunds)
        .append(view, RefundAction.CANCEL, on, now, actor, "Customer keeps it on account", null);
    verify(periods).assertPostingAllowed(tenant, on);
    verify(events).changed(row.getId());
    verify(posting).receipt(any(), eq(actor), eq("hash"), eq(row), eq(null), eq(List.of()));
  }

  @Test
  void aPaidRefundCannotBeCancelledUntilItsPaymentIsUndone() {
    view = new CollectionRefundLedger.View(refund, RefundState.SETTLED, 1, paidOn, outboundId);
    when(refunds.view(tenant, refundId)).thenReturn(view);
    when(refunds.append(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(CollectionRuleException.conflict("REFUND_TRANSITION_NOT_ALLOWED", "x"));
    var request = new CancelRefund(UUID.randomUUID(), 1L, on, now, "Changed our mind");

    assertRefused(() -> service.cancel(refundId, request, actor), "REFUND_TRANSITION_NOT_ALLOWED");
    verifyNoInteractions(events);
  }
}
