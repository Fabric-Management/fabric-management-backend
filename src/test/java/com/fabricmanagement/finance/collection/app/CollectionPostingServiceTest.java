package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.*;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.domain.*;
import com.fabricmanagement.finance.payment.dto.PaymentDto;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class CollectionPostingServiceTest {
  @Mock CollectionCommandStore commands;
  @Mock CollectionObligationRepository obligations;
  @Mock CollectionPaymentMatchRepository matches;
  @Mock CollectionProjectionRepository projections;
  @Mock CollectionProjectionService projectionService;
  @Mock CollectionAccessService access;
  @Mock PaymentService paymentService;
  @Mock PaymentRepository payments;
  @Mock FinancialPeriodGuard periods;
  @Mock CollectionEvents events;
  @Mock CollectionRefundLedger refundLedger;
  @Mock CollectionMatchReversal matchReversal;
  @Mock CollectionChequeApplicationRepository chequeApplications;
  @InjectMocks CollectionPostingService service;
  final UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      id = UUID.randomUUID(),
      partner = UUID.randomUUID(),
      paymentId = UUID.randomUUID();
  final LocalDate date = LocalDate.of(2026, 10, 20);
  final Instant now = Instant.parse("2026-10-20T12:00:00Z");
  CollectionObligation row;
  Payment payment;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), partner, "GBP");
    row.setId(id);
    row.setTenantId(tenant);
    row.revise(
        UUID.randomUUID(),
        new BigDecimal("4284.00"),
        Kind.INSTALMENT,
        Method.BANK_TRANSFER,
        date,
        false,
        null,
        now,
        false);
    payment =
        Payment.builder()
            .tradingPartnerId(partner)
            .paymentNumber("PAY-GB-0042")
            .direction(PaymentDirection.INBOUND)
            .method(PaymentMethod.BANK_TRANSFER)
            .amount(Money.of(new BigDecimal("4284.00"), "GBP"))
            .paymentDate(date)
            .build();
    payment.setId(paymentId);
    payment.setTenantId(tenant);
    lenient().when(obligations.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(row));
    lenient()
        .when(payments.findByTenantIdAndId(tenant, paymentId))
        .thenReturn(Optional.of(payment));
    var projection = CollectionProjection.create(row.getOrderId(), row.getPlanId());
    projection.observed(row.getRevisionId(), 1, 1L, false, false, now);
    lenient()
        .when(projections.findByTenantIdAndOrderIdIn(tenant, List.of(row.getOrderId())))
        .thenReturn(List.of(projection));
    lenient().when(matches.matchedPayment(tenant, paymentId)).thenReturn(BigDecimal.ZERO);
    lenient().when(refundLedger.held(tenant, paymentId)).thenReturn(BigDecimal.ZERO);
    lenient()
        .when(matches.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              var value = (CollectionPaymentMatch) call.getArgument(0);
              value.setId(UUID.randomUUID());
              return value;
            });
    lenient().when(obligations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    lenient()
        .when(commands.record(any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(mock(CollectionCommandReceipt.class));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void partialMatchUsesExistingMoneyAndPreservesTheRemainingBalance() {
    service.match(id, request("1000.00"), actor);
    ArgumentCaptor<CollectionPaymentMatch> saved =
        ArgumentCaptor.forClass(CollectionPaymentMatch.class);
    verify(matches).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getPaymentId()).isEqualTo(paymentId);
    assertThat(saved.getValue().getAmount()).isEqualByComparingTo("1000.00");
    verifyNoInteractions(paymentService);
    verify(events).changed(id);
  }

  @Test
  void rejectsBothObligationAndPaymentOvermatch() {
    var balance = mock(CollectionPaymentMatchRepository.Balance.class);
    when(balance.getAmount()).thenReturn(new BigDecimal("1000.00"));
    when(matches.settled(tenant, List.of(id))).thenReturn(List.of(balance));
    assertThatThrownBy(() -> service.match(id, request("4000.00"), actor))
        .isInstanceOf(CollectionRuleException.class);
    when(matches.settled(tenant, List.of(id))).thenReturn(List.of());
    when(matches.matchedPayment(tenant, paymentId)).thenReturn(new BigDecimal("4000.00"));
    assertThatThrownBy(() -> service.match(id, request("1000.00"), actor))
        .isInstanceOf(CollectionRuleException.class);
    verify(matches, never()).saveAndFlush(any());
  }

  @Test
  void moneySetAsideForARefundIsNotAvailableToMatchAgain() {
    when(matches.matchedPayment(tenant, paymentId)).thenReturn(new BigDecimal("4000.00"));
    when(refundLedger.held(tenant, paymentId)).thenReturn(new BigDecimal("200.00"));

    assertThatThrownBy(() -> service.match(id, request("100.00"), actor))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("COLLECTION_PAYMENT_OVERMATCH"));
    verify(matches, never()).saveAndFlush(any());
  }

  @Test
  void whatIsLeftAfterTheRefundHoldCanStillBeMatched() {
    when(matches.matchedPayment(tenant, paymentId)).thenReturn(new BigDecimal("4000.00"));
    when(refundLedger.held(tenant, paymentId)).thenReturn(new BigDecimal("200.00"));

    service.match(id, request("84.00"), actor);

    verify(matches).saveAndFlush(any());
  }

  @Test
  void aPaymentHoldsARefundOnlyWhileSomethingIsSetAside() {
    when(refundLedger.held(tenant, paymentId)).thenReturn(new BigDecimal("0.01"));
    assertThat(service.holdsRefund(tenant, paymentId)).isTrue();
    when(refundLedger.held(tenant, paymentId)).thenReturn(BigDecimal.ZERO);
    assertThat(service.holdsRefund(tenant, paymentId)).isFalse();
  }

  @Test
  void rejectsWrongPartnerCurrencyDirectionAndVoidedPayments() {
    payment.setTradingPartnerId(UUID.randomUUID());
    assertThatThrownBy(() -> service.match(id, request("1.00"), actor))
        .isInstanceOf(CollectionRuleException.class);
    payment.setTradingPartnerId(partner);
    payment.setAmount(Money.of(new BigDecimal("4284.00"), "EUR"));
    assertThatThrownBy(() -> service.match(id, request("1.00"), actor))
        .isInstanceOf(CollectionRuleException.class);
    payment.setAmount(Money.of(new BigDecimal("4284.00"), "GBP"));
    payment.setDirection(PaymentDirection.OUTBOUND);
    assertThatThrownBy(() -> service.match(id, request("1.00"), actor))
        .isInstanceOf(CollectionRuleException.class);
    payment.setDirection(PaymentDirection.INBOUND);
    payment.voidPayment();
    assertThatThrownBy(() -> service.match(id, request("1.00"), actor))
        .isInstanceOf(CollectionRuleException.class);
    verify(matches, never()).saveAndFlush(any());
  }

  @Test
  void rejectsAReversedPaymentAndAReversalRowEvenThoughBothStayReceived() {
    payment.setReversedByPaymentId(UUID.randomUUID());
    assertThatThrownBy(() -> service.match(id, request("1.00"), actor))
        .isInstanceOf(CollectionRuleException.class);
    payment.setReversedByPaymentId(null);
    payment.setReversesPaymentId(UUID.randomUUID());
    assertThatThrownBy(() -> service.match(id, request("1.00"), actor))
        .isInstanceOf(CollectionRuleException.class);
    verify(matches, never()).saveAndFlush(any());
  }

  @Test
  void legacyChequeMethodIsNotProofOfClearing() {
    payment.setMethod(PaymentMethod.CHEQUE);
    assertThatThrownBy(() -> service.match(id, request("1000.00"), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("explicit instrument clearing");
  }

  @Test
  void replayReturnsItsReceiptBeforeCheckingOldVersionsOrCreatingMoney() {
    var request = request("1000.00");
    var receipt = mock(CollectionCommandReceipt.class);
    when(commands.replay(eq(request.operationId()), eq(actor), any()))
        .thenReturn(Optional.of(receipt));
    assertThat(service.match(id, request, actor)).isSameAs(receipt);
    verifyNoInteractions(projectionService, paymentService);
    verify(commands, never()).version(any(), any());
  }

  @Test
  void aClosedPeriodFailsBeforeAReceiptOrMatchIsWritten() {
    doThrow(new ClosedFinancialPeriodException(date, YearMonth.from(date)))
        .when(periods)
        .assertPostingAllowed(tenant, date);
    assertThatThrownBy(
            () ->
                service.receive(
                    id,
                    new CollectionRequests.Receipt(
                        UUID.randomUUID(),
                        0L,
                        Method.CASH,
                        new BigDecimal("1000.00"),
                        date,
                        now,
                        null,
                        "Customer paid in cash"),
                    actor))
        .isInstanceOf(ClosedFinancialPeriodException.class);
    verifyNoInteractions(paymentService);
    verify(matches, never()).saveAndFlush(any());
  }

  @Test
  void recordingCashCreatesOnePaymentAndLinksThatSameIdentity() {
    var answer = mock(PaymentDto.class);
    when(answer.id()).thenReturn(paymentId);
    when(paymentService.createPayment(any())).thenReturn(answer);
    service.receive(
        id,
        new CollectionRequests.Receipt(
            UUID.randomUUID(),
            0L,
            Method.BANK_TRANSFER,
            new BigDecimal("1000.00"),
            date,
            now,
            "NORTHBANK-TRANSFER-42",
            "Bank credit confirmed"),
        actor);
    verify(paymentService, times(1))
        .createPayment(
            argThat(
                body ->
                    body.tradingPartnerId().equals(partner)
                        && body.direction().equals("INBOUND")
                        && body.currency().equals("GBP")));
    verify(matches).saveAndFlush(argThat(value -> value.getPaymentId().equals(paymentId)));
  }

  @Test
  void financialInvoiceAllocationDoesNotConsumeTheSeparateCollectionMatchingAxis() {
    payment.allocate(
        UUID.randomUUID(),
        Money.of(new BigDecimal("4284.00"), "GBP"),
        Money.of(new BigDecimal("4284.00"), "GBP"),
        "GBP");
    assertThat(payment.getUnallocatedAmount().isZero()).isTrue();
    service.match(id, request("4284.00"), actor);
    verify(matches).saveAndFlush(any());
    verifyNoInteractions(paymentService);
  }

  @Test
  void cashCannotBeMatchedOverTheShareAChequeNotYetClearedAlreadyCovers() {
    pendingCheque("3000.00");

    assertThatThrownBy(() -> service.match(id, request("1500.00"), actor))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("COLLECTION_PENDING_CHEQUE"))
        .hasMessageContaining("3000.00")
        .hasMessageContaining("1284.00");
    verify(matches, never()).saveAndFlush(any());
  }

  @Test
  void cashMayStillCoverWhatNoPendingChequeCovers() {
    pendingCheque("3000.00");

    service.match(id, request("1284.00"), actor);

    verify(matches).saveAndFlush(any());
  }

  @Test
  void aReceiptInCashIsRefusedBeforeAnyMoneyIsCreatedWhenAPendingChequeCoversTheRest() {
    pendingCheque("4284.00");

    assertThatThrownBy(
            () ->
                service.receive(
                    id,
                    new CollectionRequests.Receipt(
                        UUID.randomUUID(),
                        0L,
                        Method.CASH,
                        new BigDecimal("1.00"),
                        date,
                        now,
                        null,
                        "Customer paid in cash"),
                    actor))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("COLLECTION_PENDING_CHEQUE"));
    verifyNoInteractions(paymentService);
  }

  @Test
  void aChequeClearingIsNotBlockedByTheVeryChequeThatIsClearing() {
    pendingCheque("4284.00");

    service.post(
        row,
        paymentId,
        UUID.randomUUID(),
        new BigDecimal("4284.00"),
        date,
        now,
        actor,
        "Cheque cleared");

    verify(matches).saveAndFlush(any());
    verify(chequeApplications, never()).pending(any(), any());
  }

  @Test
  void reversingAMatchTakesTheReverseScopeAndNeverFallsBackToTheWriteScope() {
    var match = existingMatch();

    service.reverse(match.getId(), reversal(), actor);

    verify(access).requireGrant(actor, "reverse");
    verify(access).require(actor, "reverse", row);
    verify(access, never()).require(actor, "write", row);
    verify(matchReversal).reverse(match, date, now, actor, "Wrong match");
  }

  @Test
  void aMatchOutsideTheReverseScopeIsNotFoundEvenToSomeoneWhoMayWriteIt() {
    var match = existingMatch();
    doThrow(new NotFoundException("Collection obligation not found"))
        .when(access)
        .require(actor, "reverse", row);

    assertThatThrownBy(() -> service.reverse(match.getId(), reversal(), actor))
        .isInstanceOf(NotFoundException.class);
    verify(access, never()).require(actor, "write", row);
    verifyNoInteractions(matchReversal);
  }

  @Test
  void aMissingReverseGrantIsRefusedAsPermissionNotAsAMissingRow() {
    doThrow(new AccessDeniedException("Missing finance permission: reverse"))
        .when(access)
        .requireGrant(actor, "reverse");

    assertThatThrownBy(() -> service.requireReverse(row, actor))
        .isInstanceOf(AccessDeniedException.class);
    verify(access, never()).require(any(), any(), any());
  }

  @Test
  void refreshingBringsTheProjectionCurrentWithoutDemandingTheAgreementStillExists() {
    service.refresh(row);

    verify(projectionService).synchronize(row.getOrderId());
    verifyNoInteractions(projections);
  }

  private void pendingCheque(String amount) {
    var balance = mock(CollectionPaymentMatchRepository.Balance.class);
    lenient().when(balance.getAmount()).thenReturn(new BigDecimal(amount));
    lenient().when(chequeApplications.pending(tenant, List.of(id))).thenReturn(List.of(balance));
  }

  private CollectionPaymentMatch existingMatch() {
    var match =
        CollectionPaymentMatch.create(
            id, paymentId, null, new BigDecimal("100.00"), date, now, actor, "Receipt");
    match.setId(UUID.randomUUID());
    lenient()
        .when(matches.findByTenantIdAndId(tenant, match.getId()))
        .thenReturn(Optional.of(match));
    return match;
  }

  private CollectionRequests.Reverse reversal() {
    return new CollectionRequests.Reverse(UUID.randomUUID(), 0L, date, now, "Wrong match");
  }

  private CollectionRequests.Match request(String amount) {
    return new CollectionRequests.Match(
        UUID.randomUUID(),
        0L,
        paymentId,
        new BigDecimal(amount),
        date,
        now,
        "Bank credit matched to buyer instalment");
  }
}
