package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionPaymentMatch;
import com.fabricmanagement.finance.collection.domain.CollectionRefund.ReleasedAllocation;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.OverSettlementDecision;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests.ResolveOverSettlement;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class CollectionOverSettlementServiceTest {
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), partner = UUID.randomUUID();
  final UUID order = UUID.randomUUID(), plan = UUID.randomUUID();
  final UUID paymentOne = UUID.randomUUID(), paymentTwo = UUID.randomUUID();
  final LocalDate first = LocalDate.of(2026, 10, 1), second = LocalDate.of(2026, 10, 5);
  final LocalDate on = LocalDate.of(2026, 10, 20);
  final Instant now = Instant.parse("2026-10-20T12:00:00Z");
  CollectionCommandStore commands;
  CollectionPostingService posting;
  CollectionObligationRepository obligations;
  CollectionPaymentMatchRepository matches;
  CollectionRefundLedger refunds;
  CollectionEvents events;
  FinancialPeriodGuard periods;
  PaymentService paymentService;
  CollectionOverSettlementService service;
  CollectionObligation row, target;
  CollectionPaymentMatch older, newer;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    commands = mock(CollectionCommandStore.class);
    posting = mock(CollectionPostingService.class);
    obligations = mock(CollectionObligationRepository.class);
    matches = mock(CollectionPaymentMatchRepository.class);
    refunds = mock(CollectionRefundLedger.class);
    events = mock(CollectionEvents.class);
    periods = mock(FinancialPeriodGuard.class);
    paymentService = mock(PaymentService.class);
    service =
        new CollectionOverSettlementService(
            commands, posting, obligations, matches, refunds, events, periods, paymentService);
    row = obligation("1000.00", false, order);
    target = obligation("1000.00", false, order);
    target.setVersion(7L);
    older = match(row, paymentOne, null, "800.00", first);
    newer = match(row, paymentTwo, null, "500.00", second);
    when(commands.hash(any(), any(), any())).thenReturn("hash");
    when(commands.replay(any(), any(), any())).thenReturn(Optional.empty());
    when(posting.writable(row.getId(), actor)).thenReturn(row);
    when(posting.settled(row.getId())).thenReturn(new BigDecimal("1300.00"));
    when(posting.settled(target.getId())).thenReturn(BigDecimal.ZERO);
    when(matches.holdingNewestFirst(tenant, row.getId())).thenReturn(List.of(newer, older));
    // Both receipts came from payments with nothing allocated to invoices and nothing set aside.
    when(posting.payment(paymentOne)).thenReturn(payment(paymentOne, "800.00"));
    when(posting.payment(paymentTwo)).thenReturn(payment(paymentTwo, "500.00"));
    when(refunds.held(eq(tenant), any())).thenReturn(BigDecimal.ZERO);
    when(matches.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              var value = (CollectionPaymentMatch) call.getArgument(0);
              value.setId(UUID.randomUUID());
              return value;
            });
    when(posting.receipt(any(), any(), any(), any(), any(), any()))
        .thenReturn(mock(CollectionCommandReceipt.class));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  CollectionObligation obligation(String amount, boolean cancelled, UUID orderId) {
    var value = CollectionObligation.create(orderId, plan, UUID.randomUUID(), partner, "GBP");
    value.setId(UUID.randomUUID());
    value.setTenantId(tenant);
    value.revise(
        UUID.randomUUID(),
        new BigDecimal(amount),
        Kind.INSTALMENT,
        Method.BANK_TRANSFER,
        on,
        false,
        null,
        now,
        cancelled);
    return value;
  }

  CollectionPaymentMatch match(
      CollectionObligation to, UUID payment, UUID cheque, String amount, LocalDate effective) {
    var value =
        CollectionPaymentMatch.create(
            to.getId(), payment, cheque, new BigDecimal(amount), effective, now, actor, "Receipt");
    value.setId(UUID.randomUUID());
    return value;
  }

  private void lowerAgreedTo(String amount) {
    row.revise(
        UUID.randomUUID(),
        new BigDecimal(amount),
        Kind.INSTALMENT,
        Method.BANK_TRANSFER,
        on,
        false,
        null,
        now,
        false);
  }

  Payment payment(UUID id, String amount) {
    var value =
        Payment.builder()
            .tradingPartnerId(partner)
            .paymentNumber("PAY-" + id)
            .direction(PaymentDirection.INBOUND)
            .method(PaymentMethod.BANK_TRANSFER)
            .amount(Money.of(new BigDecimal(amount), "GBP"))
            .paymentDate(first)
            .build();
    value.setId(id);
    return value;
  }

  /** Allocates part of the payment to an invoice, the given number of days after the payment. */
  PaymentAllocation allocate(Payment to, String amount, long days) {
    var allocation =
        to.allocate(
            UUID.randomUUID(),
            Money.of(new BigDecimal(amount), "GBP"),
            Money.of(new BigDecimal("1000.00"), "GBP"),
            "GBP");
    allocation.setId(UUID.randomUUID());
    allocation.setAllocatedAt(Instant.parse("2026-10-01T00:00:00Z").plusSeconds(days * 86_400));
    return allocation;
  }

  ResolveOverSettlement request(OverSettlementDecision decision, String amount) {
    return request(decision, amount, null);
  }

  ResolveOverSettlement request(OverSettlementDecision decision, String amount, Boolean release) {
    return new ResolveOverSettlement(
        UUID.randomUUID(),
        0L,
        decision,
        new BigDecimal(amount),
        on,
        now,
        "Customer overpaid",
        null,
        null,
        release);
  }

  ResolveOverSettlement reapply(String amount, CollectionObligation to) {
    return new ResolveOverSettlement(
        UUID.randomUUID(),
        0L,
        OverSettlementDecision.REAPPLY,
        new BigDecimal(amount),
        on,
        now,
        "Belongs to the next instalment",
        to.getId(),
        to.getVersion(),
        null);
  }

  CollectionCommandReceipt resolve(OverSettlementDecision decision, String amount) {
    return service.resolve(row.getId(), request(decision, amount), actor);
  }

  @SuppressWarnings("unchecked")
  List<UUID> receiptIds() {
    var ids = ArgumentCaptor.forClass(List.class);
    verify(posting).receipt(any(), eq(actor), any(), eq(row), any(), ids.capture());
    return ids.getValue();
  }

  @Test
  void advanceTakesTheSurplusFromTheLatestReceiptAndMatchesWhatIsLeftAgain() {
    service.resolve(row.getId(), request(OverSettlementDecision.ADVANCE, "200.00"), actor);

    verify(posting).reverseMatch(newer, on, now, actor, "Customer overpaid");
    verify(posting, never()).reverseMatch(eq(older), any(), any(), any(), any());
    var again = ArgumentCaptor.forClass(CollectionPaymentMatch.class);
    verify(matches).saveAndFlush(again.capture());
    assertThat(again.getValue().getObligationId()).isEqualTo(row.getId());
    assertThat(again.getValue().getPaymentId()).isEqualTo(paymentTwo);
    assertThat(again.getValue().getAmount()).isEqualByComparingTo("300.00");
    assertThat(again.getValue().getEffectiveDate()).isEqualTo(on);
    assertThat(again.getValue().getReason())
        .startsWith("Remainder after over-settlement resolution: ")
        .endsWith("Customer overpaid");
    assertThat(receiptIds()).containsExactly(newer.getId(), again.getValue().getId());
    verifyNoInteractions(refunds, paymentService);
    // Parking cash that is already recorded needs the current agreement, not that it still exists,
    // and only the write grant: nothing is paid back.
    verify(posting).refresh(row);
    verify(posting, never()).fresh(any());
    verify(posting, never()).requireReverse(any(), any());
    verify(events).changed(row.getId());
  }

  @Test
  void aWholeReceiptIsReversedWithoutMatchingAnythingAgain() {
    lowerAgreedTo("800.00");
    service.resolve(row.getId(), request(OverSettlementDecision.ADVANCE, "500.00"), actor);

    verify(posting).reverseMatch(newer, on, now, actor, "Customer overpaid");
    verify(matches, never()).saveAndFlush(any());
    assertThat(receiptIds()).containsExactly(newer.getId());
  }

  @Test
  void aSurplusLargerThanOneReceiptContinuesIntoTheNextOldestOne() {
    lowerAgreedTo("700.00");
    service.resolve(row.getId(), request(OverSettlementDecision.ADVANCE, "600.00"), actor);

    InOrder sequence = Mockito.inOrder(posting);
    sequence.verify(posting).reverseMatch(eq(newer), any(), any(), any(), any());
    sequence.verify(posting).reverseMatch(eq(older), any(), any(), any(), any());
    var again = ArgumentCaptor.forClass(CollectionPaymentMatch.class);
    verify(matches).saveAndFlush(again.capture());
    assertThat(again.getValue().getPaymentId()).isEqualTo(paymentOne);
    assertThat(again.getValue().getAmount()).isEqualByComparingTo("700.00");
    verify(posting).receipt(any(), eq(actor), any(), eq(row), eq(null), any());
  }

  @Test
  void aSingleSourcePaymentIsReportedOnTheReceipt() {
    service.resolve(row.getId(), request(OverSettlementDecision.ADVANCE, "100.00"), actor);

    verify(posting).receipt(any(), eq(actor), any(), eq(row), eq(paymentTwo), any());
  }

  @Test
  void refundOpensOneRefundPerPaymentTheSurplusCameFrom() {
    lowerAgreedTo("700.00");
    service.resolve(row.getId(), request(OverSettlementDecision.REFUND, "600.00"), actor);

    verify(refunds)
        .open(
            row,
            paymentTwo,
            new BigDecimal("500.00"),
            on,
            now,
            actor,
            "Customer overpaid",
            List.of());
    verify(refunds)
        .open(
            row,
            paymentOne,
            new BigDecimal("100.00"),
            on,
            now,
            actor,
            "Customer overpaid",
            List.of());
    verify(refunds, times(2)).open(any(), any(), any(), any(), any(), any(), any(), any());
    verify(posting).requireReverse(row, actor);
    verify(posting).refresh(row);
    verify(posting, never()).fresh(any());
    verifyNoInteractions(paymentService);
  }

  @Test
  void refundMayTakeMoneyHeldByAChequeClearing() {
    older = match(row, paymentOne, UUID.randomUUID(), "800.00", first);
    newer = match(row, paymentTwo, UUID.randomUUID(), "500.00", second);
    when(matches.holdingNewestFirst(tenant, row.getId())).thenReturn(List.of(newer, older));

    service.resolve(row.getId(), request(OverSettlementDecision.REFUND, "200.00"), actor);

    var again = ArgumentCaptor.forClass(CollectionPaymentMatch.class);
    verify(matches).saveAndFlush(again.capture());
    assertThat(again.getValue().getChequeId()).isEqualTo(newer.getChequeId());
    verify(refunds).open(eq(row), eq(paymentTwo), any(), any(), any(), any(), any(), eq(List.of()));
  }

  @Test
  void reapplyMovesTheSurplusToTheNamedInstalmentOfTheSameOrder() {
    when(posting.writable(target.getId(), actor)).thenReturn(target);
    when(posting.settled(target.getId()))
        .thenReturn(new BigDecimal("400.00"), new BigDecimal("600.00"));
    var payment = mock(Payment.class);
    when(posting.payment(paymentTwo)).thenReturn(payment);
    long before = target.getFinancialRevision();

    service.resolve(row.getId(), reapply("200.00", target), actor);

    verify(posting).fresh(row);
    verify(posting).requirePayment(payment, target);
    var posted = ArgumentCaptor.forClass(CollectionPaymentMatch.class);
    verify(matches, times(2)).saveAndFlush(posted.capture());
    var rest = posted.getAllValues().get(0);
    var moved = posted.getAllValues().get(1);
    assertThat(rest.getObligationId()).isEqualTo(row.getId());
    assertThat(rest.getAmount()).isEqualByComparingTo("300.00");
    assertThat(moved.getObligationId()).isEqualTo(target.getId());
    assertThat(moved.getPaymentId()).isEqualTo(paymentTwo);
    assertThat(moved.getAmount()).isEqualByComparingTo("200.00");
    assertThat(moved.getChequeId()).isNull();
    assertThat(target.getFinancialRevision()).isEqualTo(before + 1);
    verify(events).changed(target.getId());
    verifyNoInteractions(refunds);
  }

  @Test
  void reapplyLeavesMoneyHeldByAChequeClearingAlone() {
    newer = match(row, paymentTwo, UUID.randomUUID(), "500.00", second);
    when(matches.holdingNewestFirst(tenant, row.getId())).thenReturn(List.of(newer, older));
    when(posting.writable(target.getId(), actor)).thenReturn(target);
    when(posting.settled(target.getId())).thenReturn(BigDecimal.ZERO);
    when(posting.payment(paymentOne)).thenReturn(mock(Payment.class));

    service.resolve(row.getId(), reapply("200.00", target), actor);

    verify(posting).reverseMatch(eq(older), any(), any(), any(), any());
    verify(posting, never()).reverseMatch(eq(newer), any(), any(), any(), any());
  }

  @Test
  void reapplyIsRefusedWhenOnlyChequeClearingsHoldTheSurplus() {
    older = match(row, paymentOne, UUID.randomUUID(), "800.00", first);
    newer = match(row, paymentTwo, UUID.randomUUID(), "500.00", second);
    when(matches.holdingNewestFirst(tenant, row.getId())).thenReturn(List.of(newer, older));
    when(posting.writable(target.getId(), actor)).thenReturn(target);
    when(posting.settled(target.getId())).thenReturn(BigDecimal.ZERO);

    assertRefused(
        () -> service.resolve(row.getId(), reapply("200.00", target), actor),
        "OVER_SETTLEMENT_NOT_MOVABLE");
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
  }

  @Test
  void reapplyNeedsAnInstalmentOfTheSameOrderPartnerAndCurrency() {
    var elsewhere = obligation("1000.00", false, UUID.randomUUID());
    when(posting.writable(elsewhere.getId(), actor)).thenReturn(elsewhere);

    assertRefused(
        () -> service.resolve(row.getId(), reapply("200.00", elsewhere), actor),
        "OVER_SETTLEMENT_TARGET_INCOMPATIBLE");
    assertRefused(
        () -> service.resolve(row.getId(), reapply("200.00", row), actor),
        "OVER_SETTLEMENT_TARGET_INCOMPATIBLE");
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
  }

  @Test
  void reapplyCannotOverfillTheInstalmentItMovesTo() {
    when(posting.writable(target.getId(), actor)).thenReturn(target);
    when(posting.settled(target.getId())).thenReturn(new BigDecimal("900.00"));

    assertRefused(
        () -> service.resolve(row.getId(), reapply("200.00", target), actor),
        "COLLECTION_OVERMATCH");
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
    verify(matches, never()).saveAndFlush(any());
  }

  @Test
  void reapplyCannotMoveCashOverTheShareAPendingChequeAlreadyCoversOnTheTarget() {
    when(posting.writable(target.getId(), actor)).thenReturn(target);
    when(posting.settled(target.getId())).thenReturn(BigDecimal.ZERO);
    doThrow(CollectionRuleException.conflict("COLLECTION_PENDING_CHEQUE", "x"))
        .when(posting)
        .requireRoomBesideCheques(target, new BigDecimal("200.00"));

    assertRefused(
        () -> service.resolve(row.getId(), reapply("200.00", target), actor),
        "COLLECTION_PENDING_CHEQUE");
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
    verify(matches, never()).saveAndFlush(any());
  }

  @Test
  void reapplyChecksTheTargetsVersionLikeAnyOtherCommand() {
    when(posting.writable(target.getId(), actor)).thenReturn(target);

    service.resolve(row.getId(), reapply("0.01", target), actor);

    verify(commands).version(7L, 7L);
  }

  @Test
  void onlyReapplyNamesADestinationAndOnlyARefundMayReleaseInvoiceAllocations() {
    var withTarget =
        new ResolveOverSettlement(
            UUID.randomUUID(),
            0L,
            OverSettlementDecision.ADVANCE,
            new BigDecimal("1.00"),
            on,
            now,
            "Why",
            target.getId(),
            0L,
            null);
    var withoutTarget =
        new ResolveOverSettlement(
            UUID.randomUUID(),
            0L,
            OverSettlementDecision.REAPPLY,
            new BigDecimal("1.00"),
            on,
            now,
            "Why",
            null,
            null,
            null);

    var releasing = request(OverSettlementDecision.ADVANCE, "1.00", true);

    for (var bad : List.of(withTarget, withoutTarget, releasing))
      assertThatThrownBy(() -> service.resolve(row.getId(), bad, actor))
          .isInstanceOf(CollectionRuleException.class);
    verifyNoInteractions(matches, refunds, events);
  }

  @Test
  void aCancelledExpectationsWholeRecordedAmountIsTheSurplus() {
    var cancelled = obligation("1000.00", true, order);
    when(posting.writable(cancelled.getId(), actor)).thenReturn(cancelled);
    when(posting.settled(cancelled.getId())).thenReturn(new BigDecimal("300.00"));
    var held = match(cancelled, paymentOne, null, "300.00", first);
    when(matches.holdingNewestFirst(tenant, cancelled.getId())).thenReturn(List.of(held));

    service.resolve(cancelled.getId(), request(OverSettlementDecision.REFUND, "300.00"), actor);

    verify(posting).reverseMatch(eq(held), any(), any(), any(), any());
    verify(refunds)
        .open(eq(cancelled), eq(paymentOne), any(), any(), any(), any(), any(), eq(List.of()));
  }

  @Test
  void nothingIsResolvedWhenNothingIsAboveTheAgreedAmount() {
    when(posting.settled(row.getId())).thenReturn(new BigDecimal("1000.00"));

    assertRefused(
        () -> resolve(OverSettlementDecision.ADVANCE, "1.00"),
        "OVER_SETTLEMENT_NOTHING_TO_RESOLVE");
    verifyNoInteractions(refunds, events);
  }

  @Test
  void aDecisionCannotCoverMoreThanTheSurplus() {
    assertRefused(
        () -> resolve(OverSettlementDecision.REFUND, "300.01"), "OVER_SETTLEMENT_EXCEEDS_SURPLUS");
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
    verifyNoInteractions(refunds);
  }

  @Test
  void theResolutionCannotBeDatedBeforeTheReceiptItCorrects() {
    var early =
        new ResolveOverSettlement(
            UUID.randomUUID(),
            0L,
            OverSettlementDecision.ADVANCE,
            new BigDecimal("200.00"),
            second.minusDays(1),
            now,
            "Too early",
            null,
            null,
            null);

    assertThatThrownBy(() -> service.resolve(row.getId(), early, actor))
        .isInstanceOf(CollectionRuleException.class);
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
  }

  @Test
  void aClosedPeriodStopsTheResolutionBeforeAnythingIsReversed() {
    doThrow(new IllegalStateException("closed")).when(periods).assertPostingAllowed(tenant, on);

    assertThatThrownBy(() -> resolve(OverSettlementDecision.ADVANCE, "1.00"))
        .isInstanceOf(IllegalStateException.class);
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
  }

  @Test
  void aReplayedOperationReturnsItsReceiptAndChangesNothing() {
    var receipt = mock(CollectionCommandReceipt.class);
    when(commands.replay(any(), eq(actor), eq("hash"))).thenReturn(Optional.of(receipt));

    var result =
        service.resolve(row.getId(), request(OverSettlementDecision.REFUND, "100.00"), actor);

    assertThat(result).isSameAs(receipt);
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
    verifyNoInteractions(matches, refunds, events);
  }

  @Test
  void theCommandIsRejectedWhenTheExpectationWasChangedSinceItWasRead() {
    doThrow(CollectionRuleException.conflict("COLLECTION_VERSION_CONFLICT", "x"))
        .when(commands)
        .version(any(), any());

    assertRefused(
        () -> resolve(OverSettlementDecision.ADVANCE, "1.00"), "COLLECTION_VERSION_CONFLICT");
    verifyNoInteractions(matches, refunds, events);
  }

  @Test
  void advanceLeavesMoneyHeldByAChequeClearingAlone() {
    newer = match(row, paymentTwo, UUID.randomUUID(), "500.00", second);
    when(matches.holdingNewestFirst(tenant, row.getId())).thenReturn(List.of(newer, older));

    service.resolve(row.getId(), request(OverSettlementDecision.ADVANCE, "200.00"), actor);

    verify(posting).reverseMatch(eq(older), any(), any(), any(), any());
    verify(posting, never()).reverseMatch(eq(newer), any(), any(), any(), any());
  }

  @Test
  void advanceIsRefusedWhenOnlyChequeClearingsHoldTheSurplus() {
    older = match(row, paymentOne, UUID.randomUUID(), "800.00", first);
    newer = match(row, paymentTwo, UUID.randomUUID(), "500.00", second);
    when(matches.holdingNewestFirst(tenant, row.getId())).thenReturn(List.of(newer, older));

    assertRefused(
        () -> resolve(OverSettlementDecision.ADVANCE, "200.00"), "OVER_SETTLEMENT_NOT_MOVABLE");
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
    verifyNoInteractions(refunds, events);
  }

  @Test
  void owingTheSurplusBackNeedsTheReverseGrantNotMerelyTheWriteGrant() {
    doThrow(new AccessDeniedException("Missing finance permission: reverse"))
        .when(posting)
        .requireReverse(row, actor);

    assertThatThrownBy(() -> resolve(OverSettlementDecision.REFUND, "100.00"))
        .isInstanceOf(AccessDeniedException.class);
    verify(posting, never()).refresh(any());
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
    verifyNoInteractions(matches, refunds, events, paymentService);
  }

  @Test
  void aRefundIsRefusedWhenInvoiceAllocationsAlreadyUseTheMoney() {
    var used = payment(paymentTwo, "500.00");
    var allocation = allocate(used, "400.00", 1);
    when(posting.payment(paymentTwo)).thenReturn(used);

    assertThatThrownBy(() -> resolve(OverSettlementDecision.REFUND, "300.00"))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("REFUND_EXCEEDS_UNALLOCATED");
              assertThat(e.getDetails()).containsEntry("paymentId", paymentTwo.toString());
              assertThat(e.getDetails().get("allocations").toString())
                  .contains(allocation.getId().toString(), allocation.getInvoiceId().toString());
            });
    // Nothing was changed on the way: no allocation released, no receipt reversed, no refund.
    verifyNoInteractions(paymentService, events);
    verify(refunds, never()).open(any(), any(), any(), any(), any(), any(), any(), any());
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
  }

  @Test
  void moneyAlreadySetAsideForAnotherRefundIsNotAvailableToThisOne() {
    when(refunds.held(tenant, paymentTwo)).thenReturn(new BigDecimal("300.00"));

    assertRefused(
        () -> resolve(OverSettlementDecision.REFUND, "300.00"), "REFUND_EXCEEDS_UNALLOCATED");
    // There is no invoice allocation to release, so asking to release one cannot make room.
    var releasing = request(OverSettlementDecision.REFUND, "300.00", true);
    assertRefused(
        () -> service.resolve(row.getId(), releasing, actor), "REFUND_EXCEEDS_UNALLOCATED");
    verifyNoInteractions(paymentService, events);
    verify(refunds, never()).open(any(), any(), any(), any(), any(), any(), any(), any());
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
  }

  @Test
  void aRefundMayReleaseInvoiceAllocationsWhenTheSenderSaysSoAndRecordsEachOne() {
    var used = payment(paymentTwo, "500.00");
    var allocation = allocate(used, "400.00", 1);
    when(posting.payment(paymentTwo)).thenReturn(used);
    doAnswer(
            call -> {
              used.deallocate(call.getArgument(1));
              return null;
            })
        .when(paymentService)
        .deallocatePayment(eq(paymentTwo), any());

    service.resolve(row.getId(), request(OverSettlementDecision.REFUND, "300.00", true), actor);

    var released =
        new ReleasedAllocation(
            allocation.getId(),
            allocation.getInvoiceId(),
            allocation.getAmount().getAmount().toPlainString());
    InOrder sequence = Mockito.inOrder(paymentService, posting, refunds);
    sequence.verify(paymentService).deallocatePayment(paymentTwo, allocation.getId());
    sequence.verify(posting).reverseMatch(eq(newer), any(), any(), any(), any());
    sequence
        .verify(refunds)
        .open(
            row,
            paymentTwo,
            new BigDecimal("300.00"),
            on,
            now,
            actor,
            "Customer overpaid",
            List.of(released));
  }

  @Test
  void releasingIsRefusedWithTheDayOfThePaymentWhenThatPeriodIsClosed() {
    var used = payment(paymentTwo, "500.00");
    var allocation = allocate(used, "400.00", 1);
    when(posting.payment(paymentTwo)).thenReturn(used);
    doThrow(new ClosedFinancialPeriodException(first, YearMonth.from(first)))
        .when(paymentService)
        .deallocatePayment(paymentTwo, allocation.getId());

    assertThatThrownBy(
            () ->
                service.resolve(
                    row.getId(), request(OverSettlementDecision.REFUND, "300.00", true), actor))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("REFUND_PAYMENT_PERIOD_CLOSED");
              assertThat(e.getHttpStatus()).isEqualTo(409);
              assertThat(e.getArgs()).containsExactly(first.toString());
              assertThat(e.getDetails()).containsEntry("paymentDate", first.toString());
            });
    // The refund was not opened and the receipt was not taken back.
    verify(refunds, never()).open(any(), any(), any(), any(), any(), any(), any(), any());
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
    verifyNoInteractions(events);
  }

  @Test
  void releasingStopsAsSoonAsTheRefundFitsAndStartsWithTheNewestAllocation() {
    var used = payment(paymentTwo, "500.00");
    var oldest = allocate(used, "100.00", 1);
    var newest = allocate(used, "250.00", 2);
    when(posting.payment(paymentTwo)).thenReturn(used);
    doAnswer(
            call -> {
              used.deallocate(call.getArgument(1));
              return null;
            })
        .when(paymentService)
        .deallocatePayment(eq(paymentTwo), any());

    service.resolve(row.getId(), request(OverSettlementDecision.REFUND, "300.00", true), actor);

    verify(paymentService).deallocatePayment(paymentTwo, newest.getId());
    verify(paymentService, never()).deallocatePayment(paymentTwo, oldest.getId());
    var released =
        new ReleasedAllocation(
            newest.getId(), newest.getInvoiceId(), newest.getAmount().getAmount().toPlainString());
    verify(refunds)
        .open(eq(row), eq(paymentTwo), any(), any(), any(), any(), any(), eq(List.of(released)));
  }

  @Test
  void releasingIsNeverStartedForRoomThatIsAlreadyThere() {
    var used = payment(paymentTwo, "900.00");
    allocate(used, "400.00", 1);
    when(posting.payment(paymentTwo)).thenReturn(used);

    service.resolve(row.getId(), request(OverSettlementDecision.REFUND, "300.00", true), actor);

    verifyNoInteractions(paymentService);
    verify(refunds).open(eq(row), eq(paymentTwo), any(), any(), any(), any(), any(), eq(List.of()));
  }

  private static void assertRefused(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            CollectionRuleException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
  }
}
