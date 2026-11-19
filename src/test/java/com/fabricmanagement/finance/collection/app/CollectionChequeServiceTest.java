package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
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
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class CollectionChequeServiceTest {
  @Mock CollectionCommandStore commands;
  @Mock CollectionChequeRepository cheques;
  @Mock CollectionChequeApplicationRepository applications;
  @Mock CollectionChequeEventRepository events;
  @Mock CollectionObligationRepository obligations;
  @Mock CollectionPaymentMatchRepository matches;
  @Mock CollectionPostingService posting;
  @Mock CollectionAccessService access;
  @Mock PaymentService payments;
  @Mock FinancialPeriodGuard periods;
  @Mock CollectionEvents changes;
  @Mock CollectionChequeDrawerResolver drawers;
  @Mock CollectionRefundLedger refundLedger;
  @InjectMocks CollectionChequeService service;
  UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      partner = UUID.randomUUID(),
      rowId = UUID.randomUUID(),
      chequeId = UUID.randomUUID(),
      paymentId = UUID.randomUUID();
  LocalDate date = LocalDate.of(2026, 10, 20);
  Instant now = Instant.parse("2026-10-20T12:00:00Z");
  CollectionObligation row;
  CollectionCheque cheque;
  Payment payment;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), partner, "GBP");
    row.setId(rowId);
    row.setTenantId(tenant);
    row.revise(
        UUID.randomUUID(),
        new BigDecimal("4284.00"),
        Kind.INSTALMENT,
        Method.CHEQUE,
        date,
        false,
        date.plusMonths(3),
        now,
        false);
    cheque =
        CollectionCheque.receive(
            partner,
            "GBP",
            new BigDecimal("4284.00"),
            date,
            date.plusMonths(3),
            "NORTHBANK-0042",
            "Signed instrument scan",
            new CollectionCheque.Drawer(
                com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.OWN,
                null,
                "Northbridge Cloth"));
    cheque.setId(chequeId);
    cheque.setTenantId(tenant);
    payment =
        Payment.builder()
            .tradingPartnerId(partner)
            .paymentNumber("PAY-GB-0042")
            .direction(PaymentDirection.INBOUND)
            .method(PaymentMethod.CHEQUE)
            .amount(Money.of(new BigDecimal("4284.00"), "GBP"))
            .paymentDate(date)
            .build();
    payment.setId(paymentId);
    lenient().when(posting.writableBatch(List.of(rowId), actor)).thenReturn(List.of(row));
    lenient()
        .when(obligations.findByTenantIdAndIdIn(tenant, List.of(rowId)))
        .thenReturn(List.of(row));
    lenient()
        .when(access.reach(actor, "write"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.ORGANIZATION, Set.of(actor)));
    lenient()
        .when(access.reach(actor, "reverse"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.ORGANIZATION, Set.of(actor)));
    lenient().when(cheques.findByTenantIdAndId(tenant, chequeId)).thenReturn(Optional.of(cheque));
    lenient()
        .when(cheques.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              CollectionCheque value = call.getArgument(0);
              value.setId(chequeId);
              return value;
            });
    lenient()
        .when(applications.active(tenant, chequeId))
        .thenReturn(List.of(application("4284.00")));
    lenient().when(matches.nonChequeSettled(tenant, rowId)).thenReturn(BigDecimal.ZERO);
    lenient().when(matches.matchedPayment(tenant, paymentId)).thenReturn(BigDecimal.ZERO);
    lenient().when(posting.payment(paymentId)).thenReturn(payment);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void appointedFinanceOwnerCanPresentAnInstrumentRecordedByAColleague() {
    cheque.setCreatedBy(UUID.randomUUID());
    row.assignResponsible(actor);
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    service.transition(chequeId, transition(ChequeAction.PRESENT), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.PRESENTED);
  }

  @Test
  void oneHiddenApplicationPreventsAccessToTheWholeInstrument() {
    row.assignResponsible(UUID.randomUUID());
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThatThrownBy(() -> service.transition(chequeId, transition(ChequeAction.PRESENT), actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    verifyNoInteractions(payments);
  }

  @Test
  void physicalReceiptCreatesInstrumentEvidenceWithoutCreatingOrMatchingAnyCash() {
    when(drawers.forReceipt(any()))
        .thenReturn(
            new CollectionCheque.Drawer(
                com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.OWN,
                null,
                "Northbridge Cloth"));
    service.receive(
        new CollectionRequests.ReceiveCheque(
            UUID.randomUUID(),
            partner,
            "GBP",
            new BigDecimal("4284.00"),
            date,
            date.plusMonths(3),
            "NORTHBANK-0042",
            "Signed instrument scan",
            now,
            "Cheque handed to finance",
            List.of(new CollectionRequests.Application(rowId, 0L, new BigDecimal("4284.00"))),
            com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.OWN,
            null,
            null),
        actor);
    verifyNoInteractions(payments);
    verify(posting, never())
        .postChequeBatch(any(), any(), anyList(), anyList(), any(), any(), any(), any());
    verify(events).saveAndFlush(argThat(value -> value.getState() == ChequeState.RECEIVED));
  }

  /**
   * PAYMENT-PLAN-7 R16, B11: a receipt stored before tagging replays; its kind is never checked.
   */
  @Test
  void aReceiptWithoutAKindStillReplaysWithoutAskingForADrawer() {
    var stored = mock(CollectionCommandReceipt.class);
    when(commands.replay(any(), any(), any())).thenReturn(Optional.of(stored));
    var answer =
        service.receive(
            new CollectionRequests.ReceiveCheque(
                UUID.randomUUID(),
                partner,
                "GBP",
                new BigDecimal("4284.00"),
                date,
                date.plusMonths(3),
                "NORTHBANK-0042",
                "Signed instrument scan",
                now,
                "Cheque handed to finance",
                List.of(new CollectionRequests.Application(rowId, 0L, new BigDecimal("4284.00"))),
                null,
                null,
                null),
            actor);
    assertThat(answer).isSameAs(stored);
    verifyNoInteractions(drawers);
    verify(cheques, never()).saveAndFlush(any());
  }

  @Test
  void clearingFailureNeverAdvancesTheInstrumentToCleared() {
    var answer = mock(PaymentDto.class);
    when(answer.id()).thenReturn(paymentId);
    when(payments.createPayment(any())).thenReturn(answer);
    when(posting.postChequeBatch(any(), any(), anyList(), anyList(), any(), any(), any(), any()))
        .thenThrow(
            CollectionRuleException.conflict(
                "COLLECTION_OVERMATCH", "Buyer paid this instalment elsewhere"));
    assertThatThrownBy(() -> service.transition(chequeId, transition(ChequeAction.CLEAR), actor))
        .isInstanceOf(CollectionRuleException.class);
    assertThat(cheque.getState()).isEqualTo(ChequeState.RECEIVED);
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void clearingUsesOnePaymentAndCanApplyItToTheRecordedExpectation() {
    var answer = mock(PaymentDto.class);
    when(answer.id()).thenReturn(paymentId);
    when(payments.createPayment(any())).thenReturn(answer);
    var match =
        CollectionPaymentMatch.create(
            rowId,
            paymentId,
            chequeId,
            new BigDecimal("4284.00"),
            date,
            now,
            actor,
            "Bank clearing verified");
    match.setId(UUID.randomUUID());
    when(posting.postChequeBatch(
            eq(paymentId),
            eq(chequeId),
            anyList(),
            anyList(),
            eq(date),
            eq(now),
            eq(actor),
            anyString()))
        .thenReturn(List.of(match.getId()));
    service.transition(chequeId, transition(ChequeAction.CLEAR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    assertThat(cheque.getClearedPaymentId()).isEqualTo(paymentId);
    verify(payments, times(1)).createPayment(any());
    verify(events).saveAndFlush(argThat(value -> value.getAction().equals("CLEAR")));
  }

  @Test
  void dishonourCancelsTheClearingPaymentOnADatedReversalNeverAVoid() {
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    service.transition(chequeId, transition(ChequeAction.DISHONOUR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.DISHONOURED);
    verify(payments)
        .reverseReleasedInstrument(
            eq(paymentId),
            argThat(
                value ->
                    value.reversalDate().equals(date)
                        && value.reason().equals("Bank evidence reviewed")),
            eq(actor));
    verify(payments, never()).voidPayment(any());
    verify(payments, never()).reverse(any(), any(), any());
  }

  /**
   * PAYMENT-PLAN-9 S4: a refund on the clearing payment that belongs to an obligation the cheque
   * never covered (its remainder was matched later, R4) blocks undoing the clearing.
   */
  @Test
  void aRefundOfAnotherObligationOnTheClearingPaymentBlocksTheDishonour() {
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    when(refundLedger.holdingObligations(tenant, paymentId)).thenReturn(Set.of(UUID.randomUUID()));
    assertThatThrownBy(
            () -> service.transition(chequeId, transition(ChequeAction.DISHONOUR), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_CLEARING_HAS_OTHER_BINDINGS");
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    verifyNoInteractions(payments);
    // A refund of an obligation the cheque covers is the cheque's own flow and does not block.
    when(refundLedger.holdingObligations(tenant, paymentId)).thenReturn(Set.of(rowId));
    service.transition(chequeId, transition(ChequeAction.DISHONOUR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.DISHONOURED);
  }

  @Test
  void returningAClearedInstrumentReversesItsPaymentToo() {
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    service.transition(chequeId, transition(ChequeAction.RETURN), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.RETURNED);
    verify(payments).reverseReleasedInstrument(eq(paymentId), any(), eq(actor));
  }

  /**
   * Dishonouring or returning a cleared instrument reverses its matches and its payment. The write
   * grant must not reach that effect through the instrument when reversing a match or cancelling a
   * refund takes the reverse grant.
   */
  @Test
  void dishonouringAClearedInstrumentTakesTheReverseGrantNotMerelyWrite() {
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    withoutReverseGrant();
    assertThatThrownBy(
            () -> service.transition(chequeId, transition(ChequeAction.DISHONOUR), actor))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    verifyNoInteractions(payments);
    verify(posting, never()).reverseMatch(any(), any(), any(), any(), any());
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void returningAClearedInstrumentTakesTheReverseGrantNotMerelyWrite() {
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    withoutReverseGrant();
    assertThatThrownBy(() -> service.transition(chequeId, transition(ChequeAction.RETURN), actor))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    verifyNoInteractions(payments);
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void aReverseGrantThatDoesNotReachEveryLinkedRowHidesTheInstrument() {
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    row.assignResponsible(UUID.randomUUID());
    when(access.reach(actor, "reverse"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThatThrownBy(
            () -> service.transition(chequeId, transition(ChequeAction.DISHONOUR), actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    verifyNoInteractions(payments);
  }

  @Test
  void anInstrumentThatMovedNoMoneyNeedsOnlyTheWriteGrantToBeDishonouredOrReturned() {
    withoutReverseGrant();
    service.transition(chequeId, transition(ChequeAction.PRESENT), actor);
    service.transition(chequeId, transition(ChequeAction.DISHONOUR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.DISHONOURED);
    service.transition(chequeId, transition(ChequeAction.RETURN), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.RETURNED);
    verifyNoInteractions(payments);
  }

  private void withoutReverseGrant() {
    lenient()
        .when(access.reach(actor, "reverse"))
        .thenReturn(new CollectionAccessService.Reach(tenant, null, Set.of(actor)));
  }

  @Test
  void aClearingPaymentThatIsAlreadyGoneIsNotReversedAgain() {
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    payment.voidPayment();
    service.transition(chequeId, transition(ChequeAction.DISHONOUR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.DISHONOURED);
    verify(payments, never()).reverseReleasedInstrument(any(), any(), any());
  }

  /**
   * Policy: the dishonour is posted on its own effective date, not on the clearing payment's. A
   * clearing in a closed period can therefore still be dishonoured on an open date, while a closed
   * dishonour date refuses the whole transition: no dishonour evidence and no command receipt may
   * be left behind.
   */
  @Test
  void dishonourIsRefusedAtomicallyWhenItsOwnEffectiveDateIsInAClosedPeriod() {
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    doThrow(new ClosedFinancialPeriodException(date, YearMonth.from(date)))
        .when(payments)
        .reverseReleasedInstrument(eq(paymentId), any(), eq(actor));
    assertThatThrownBy(
            () -> service.transition(chequeId, transition(ChequeAction.DISHONOUR), actor))
        .isInstanceOf(ClosedFinancialPeriodException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClosedFinancialPeriodException.ERROR_CODE);
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    verify(events, never()).saveAndFlush(any());
    verify(commands, never()).record(any(), any(), any(), any(), any(), anyLong(), any(), any());
  }

  @Test
  void returningAnUnclearedInstrumentDoesNotVoidMoney() {
    service.transition(chequeId, transition(ChequeAction.RETURN), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.RETURNED);
    verifyNoInteractions(payments);
  }

  private CollectionChequeApplication application(String amount) {
    return CollectionChequeApplication.create(
        chequeId, rowId, new BigDecimal(amount), date, now, actor, "Cheque handed to finance");
  }

  private CollectionRequests.ReceiveCheque receipt() {
    return receipt(
        List.of(new CollectionRequests.Application(rowId, 0L, new BigDecimal("2000.00"))));
  }

  private CollectionRequests.ReceiveCheque receipt(List<CollectionRequests.Application> applied) {
    return new CollectionRequests.ReceiveCheque(
        UUID.randomUUID(),
        partner,
        "GBP",
        new BigDecimal("4284.00"),
        date,
        date.plusMonths(3),
        "NORTHBANK-0042",
        "Signed instrument scan",
        now,
        "Cheque handed to finance",
        applied,
        DrawerKind.OWN,
        null,
        null);
  }

  // ---- PAYMENT-PLAN-9 S2: a cheque held for the customer, applied to nothing --------------------

  private void unlinked() {
    when(applications.active(tenant, chequeId)).thenReturn(List.of());
    lenient().when(posting.writableBatch(List.of(), actor)).thenReturn(List.of());
  }

  private CollectionRequests.Transition unlinked(ChequeAction action) {
    return new CollectionRequests.Transition(
        UUID.randomUUID(),
        0L,
        action,
        date,
        now,
        "Bank evidence reviewed",
        "Statement and instrument reference verified",
        null,
        null,
        null);
  }

  @Test
  void aChequeCanBeReceivedForTheCustomerWithoutAnyApplication() {
    when(posting.writableBatch(List.of(), actor)).thenReturn(List.of());
    when(drawers.forReceipt(any()))
        .thenReturn(new CollectionCheque.Drawer(DrawerKind.OWN, null, "Northbridge Cloth"));
    service.receive(receipt(List.of()), actor);
    verify(cheques).saveAndFlush(argThat(value -> value.getState() == ChequeState.RECEIVED));
    verify(events).saveAndFlush(argThat(value -> value.getAction().equals("RECEIVE")));
    verify(applications, never()).save(any());
    verify(applications, never()).received(any(), any());
    verify(matches, never()).nonChequeSettledFor(any(), any());
    verifyNoInteractions(payments);
    noObligationTouched();
  }

  private void noObligationTouched() {
    verify(obligations, never()).save(any());
    verify(obligations, never()).findByTenantIdAndIdIn(any(), any());
    verify(changes, never()).changed(any());
  }

  /** R1, R6: a cheque held for the customer has no target, so the write grant is checked itself. */
  @Test
  void aChequeHeldForTheCustomerStillNeedsTheFinanceWriteGrant() {
    doThrow(new AccessDeniedException("Missing finance permission: write"))
        .when(access)
        .requireGrant(actor, "write");
    assertThatThrownBy(() -> service.receive(receipt(List.of()), actor))
        .isInstanceOf(AccessDeniedException.class);
    verify(cheques, never()).saveAndFlush(any());
    verify(events, never()).saveAndFlush(any());
  }

  /** A cheque held for the customer alone must still name a customer of this company. */
  @Test
  void aChequeHeldForAnUnknownCustomerIsRefused() {
    when(posting.writableBatch(List.of(), actor)).thenReturn(List.of());
    doThrow(CollectionRuleException.invalid("COLLECTION_PARTNER_NOT_FOUND", "Unknown customer"))
        .when(drawers)
        .requireCustomer(partner);
    assertThatThrownBy(() -> service.receive(receipt(List.of()), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_PARTNER_NOT_FOUND");
    verify(cheques, never()).saveAndFlush(any());
  }

  /** PAYMENT-PLAN-9 C1: a supplier-only partner hands over no cheque; nothing is recorded. */
  @Test
  void aChequeFromAPartnerThatIsNotACustomerIsRefusedWithItsCode() {
    lenient().when(posting.writableBatch(List.of(), actor)).thenReturn(List.of());
    doThrow(CollectionRuleException.conflict("COLLECTION_PARTNER_NOT_CUSTOMER", "Not a customer"))
        .when(drawers)
        .requireCustomer(partner);
    assertThatThrownBy(() -> service.receive(receipt(List.of()), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "COLLECTION_PARTNER_NOT_CUSTOMER");
    verify(cheques, never()).saveAndFlush(any());
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void anUnlinkedChequeCannotBeClearedBeforeTheDateOfAnExistingPayment() {
    unlinked();
    var later =
        Payment.builder()
            .tradingPartnerId(partner)
            .paymentNumber("PAY-GB-0043")
            .direction(PaymentDirection.INBOUND)
            .method(PaymentMethod.CHEQUE)
            .amount(Money.of(new BigDecimal("4284.00"), "GBP"))
            .paymentDate(date.plusDays(5))
            .build();
    UUID laterId = UUID.randomUUID();
    later.setId(laterId);
    when(posting.payment(laterId)).thenReturn(later);
    when(matches.matchedPayment(tenant, laterId)).thenReturn(BigDecimal.ZERO);
    var clear =
        new CollectionRequests.Transition(
            UUID.randomUUID(),
            0L,
            ChequeAction.CLEAR,
            date,
            now,
            "Bank evidence reviewed",
            "Statement and instrument reference verified",
            laterId,
            null,
            null);
    assertThatThrownBy(() -> service.transition(chequeId, clear, actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("precede the payment date");
    assertThat(cheque.getState()).isEqualTo(ChequeState.RECEIVED);
  }

  @Test
  void anUnlinkedChequeIsPresentedAndClearedIntoOnePaymentWithNoMatch() {
    unlinked();
    service.transition(chequeId, unlinked(ChequeAction.PRESENT), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.PRESENTED);
    var answer = mock(PaymentDto.class);
    when(answer.id()).thenReturn(paymentId);
    when(payments.createPayment(any())).thenReturn(answer);
    service.transition(chequeId, unlinked(ChequeAction.CLEAR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    assertThat(cheque.getClearedPaymentId()).isEqualTo(paymentId);
    verify(payments)
        .createPayment(argThat(value -> value.amount().compareTo(new BigDecimal("4284.00")) == 0));
    verify(posting, never())
        .postChequeBatch(any(), any(), anyList(), anyList(), any(), any(), any(), any());
    noObligationTouched();
  }

  @Test
  void anUnlinkedChequeIsSentNoObligationVersions() {
    unlinked();
    assertThatThrownBy(() -> service.transition(chequeId, transition(ChequeAction.CLEAR), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("versions do not match");
    assertThat(cheque.getState()).isEqualTo(ChequeState.RECEIVED);
    verifyNoInteractions(payments);
  }

  @Test
  void anUnlinkedChequeIsReturnedCancelledOrDishonouredWithoutTouchingAnyObligation() {
    unlinked();
    service.transition(chequeId, unlinked(ChequeAction.PRESENT), actor);
    service.transition(chequeId, unlinked(ChequeAction.DISHONOUR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.DISHONOURED);
    service.transition(chequeId, unlinked(ChequeAction.RETURN), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.RETURNED);
    verifyNoInteractions(payments);
    noObligationTouched();
  }

  @Test
  void anUnlinkedChequeCanBeCancelledOrCorrected() {
    unlinked();
    service.transition(chequeId, correction("6000.00"), actor);
    assertThat(cheque.getAmount()).isEqualByComparingTo("6000.00");
    service.transition(chequeId, unlinked(ChequeAction.CANCEL), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.CANCELLED);
    verifyNoInteractions(payments);
    noObligationTouched();
  }

  /** R6: with no obligation, the finance grant's scope applies to the cheque's creator. */
  @Test
  void anUnlinkedChequeIsReachedThroughItsCreatorUnderOwnAndDepartmentScope() {
    unlinked();
    UUID colleague = UUID.randomUUID();
    cheque.setCreatedBy(UUID.randomUUID());
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThatThrownBy(() -> service.transition(chequeId, unlinked(ChequeAction.PRESENT), actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    when(access.reach(actor, "write"))
        .thenReturn(
            new CollectionAccessService.Reach(
                tenant, DataScope.DEPARTMENT, Set.of(actor, colleague)));
    assertThatThrownBy(() -> service.transition(chequeId, unlinked(ChequeAction.PRESENT), actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    assertThat(cheque.getState()).isEqualTo(ChequeState.RECEIVED);
    cheque.setCreatedBy(colleague);
    service.transition(chequeId, unlinked(ChequeAction.PRESENT), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.PRESENTED);
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    cheque.setCreatedBy(actor);
    service.transition(chequeId, unlinked(ChequeAction.DISHONOUR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.DISHONOURED);
  }

  // ---- PAYMENT-PLAN-9 S3: allocate later and reverse a wrong application ------------------------

  private CollectionRequests.AllocateCheque allocation(String amount) {
    return new CollectionRequests.AllocateCheque(
        UUID.randomUUID(),
        0L,
        date,
        now,
        "Buyer asked to use the cheque for this instalment",
        "E-mail from the buyer's accounts team",
        List.of(new CollectionRequests.Application(rowId, 0L, new BigDecimal(amount))));
  }

  private CollectionRequests.ReverseChequeApplication reversal() {
    return new CollectionRequests.ReverseChequeApplication(
        UUID.randomUUID(),
        0L,
        0L,
        date,
        now,
        "Applied to the wrong instalment",
        "Buyer's remittance advice names another order");
  }

  private CollectionChequeApplication stored(String amount) {
    var value = application(amount);
    value.setId(UUID.randomUUID());
    value.setTenantId(tenant);
    value.setCreatedAt(now);
    return value;
  }

  private void allocatable() {
    unlinked();
    lenient()
        .when(applications.save(any()))
        .thenAnswer(
            call -> {
              CollectionChequeApplication value = call.getArgument(0);
              value.setId(UUID.randomUUID());
              return value;
            });
  }

  @Test
  void aHeldChequeIsAllocatedLaterWithItsOwnEvidenceAndAnApplyEvent() {
    allocatable();
    service.allocate(chequeId, allocation("2000.00"), actor);
    var saved = ArgumentCaptor.forClass(CollectionChequeApplication.class);
    verify(applications).save(saved.capture());
    assertThat(saved.getValue().getAmount()).isEqualByComparingTo("2000.00");
    assertThat(saved.getValue().getEffectiveDate()).isEqualTo(date);
    assertThat(saved.getValue().getActorId()).isEqualTo(actor);
    assertThat(saved.getValue().getReason())
        .isEqualTo("Buyer asked to use the cheque for this instalment");
    verify(events)
        .save(
            argThat(
                (CollectionChequeEvent event) ->
                    event.getAction().equals("APPLY")
                        && saved.getValue().getId().equals(event.getApplicationId())
                        && event.getState() == ChequeState.RECEIVED
                        && event.getEvidenceRevision() == 1L));
    verify(changes).changed(rowId);
    assertThat(cheque.getState()).isEqualTo(ChequeState.RECEIVED);
    verifyNoInteractions(payments);
  }

  @Test
  void anAllocationAboveTheUnappliedAmountIsRefused() {
    allocatable();
    when(applications.active(tenant, chequeId)).thenReturn(List.of(application("2000.00")));
    when(posting.writableBatch(List.of(rowId), actor)).thenReturn(List.of(row));
    assertThatThrownBy(() -> service.allocate(chequeId, allocation("2284.01"), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("unapplied amount");
    verify(applications, never()).save(any());
  }

  @Test
  void anAllocationAboveWhatTheInstalmentStillLacksIsRefused() {
    allocatable();
    var covered = mock(CollectionPaymentMatchRepository.Balance.class);
    when(covered.getId()).thenReturn(rowId);
    when(covered.getAmount()).thenReturn(new BigDecimal("3000.00"));
    when(applications.received(tenant, List.of(rowId))).thenReturn(List.of(covered));
    assertThatThrownBy(() -> service.allocate(chequeId, allocation("1284.01"), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("missing instrument amount");
    verify(applications, never()).save(any());
  }

  @Test
  void aClearedChequeIsNeverAllocatedItsPaymentIs() {
    allocatable();
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    assertThatThrownBy(() -> service.allocate(chequeId, allocation("1000.00"), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_APPLICATION_NOT_ALLOWED");
    verify(applications, never()).save(any());
    verify(events, never()).save(any());
  }

  @Test
  void aReplayedAllocationReturnsTheOriginalResultAndRecordsNothing() {
    var stored = mock(CollectionCommandReceipt.class);
    when(commands.replay(any(), any(), any())).thenReturn(Optional.of(stored));
    assertThat(service.allocate(chequeId, allocation("1000.00"), actor)).isSameAs(stored);
    // The replay answers before the cheque's current applications are read (S3 review).
    verify(applications, never()).active(any(), any());
    verify(applications, never()).save(any());
  }

  @Test
  void aWrongApplicationIsReversedWithItsEvidenceAndTheChequeStaysHeld() {
    var wrong = stored("2000.00");
    when(applications.findByTenantIdAndId(tenant, wrong.getId())).thenReturn(Optional.of(wrong));
    when(applications.active(tenant, chequeId)).thenReturn(List.of(wrong));
    service.reverseApplication(chequeId, wrong.getId(), reversal(), actor);
    assertThat(wrong.isReversed()).isTrue();
    assertThat(wrong.getReversedOn()).isEqualTo(date);
    assertThat(wrong.getReversedBy()).isEqualTo(actor);
    assertThat(wrong.getReversalReason()).isEqualTo("Applied to the wrong instalment");
    assertThat(wrong.getReversedAt()).isAfterOrEqualTo(wrong.getCreatedAt());
    verify(applications).saveAndFlush(wrong);
    verify(obligations).saveAndFlush(row);
    verify(events)
        .saveAndFlush(
            argThat(
                (CollectionChequeEvent event) ->
                    event.getAction().equals("REVERSE_APPLICATION")
                        && wrong.getId().equals(event.getApplicationId())));
    assertThat(cheque.getState()).isEqualTo(ChequeState.RECEIVED);
    assertThat(cheque.getAmount()).isEqualByComparingTo("4284.00");
    verifyNoInteractions(payments);
  }

  @Test
  void anApplicationIsNotReversedAfterClearingOrTwice() {
    var wrong = stored("2000.00");
    when(applications.findByTenantIdAndId(tenant, wrong.getId())).thenReturn(Optional.of(wrong));
    when(applications.active(tenant, chequeId)).thenReturn(List.of(wrong));
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    assertThatThrownBy(() -> service.reverseApplication(chequeId, wrong.getId(), reversal(), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_APPLICATION_NOT_ALLOWED");
    assertThat(wrong.isReversed()).isFalse();
    verifyNoInteractions(payments);

    var held = stored("1000.00");
    held.reverse(date, now, now, actor, "Applied to the wrong instalment");
    cheque.transition(ChequeAction.DISHONOUR, null, "Bank returned it unpaid");
    cheque.transition(ChequeAction.PRESENT, null, "Presented again");
    when(applications.findByTenantIdAndId(tenant, held.getId())).thenReturn(Optional.of(held));
    assertThatThrownBy(() -> service.reverseApplication(chequeId, held.getId(), reversal(), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasFieldOrPropertyWithValue("errorCode", "CHEQUE_APPLICATION_ALREADY_REVERSED");
  }

  /**
   * D18: a reversal that removed the cheque's last application is replayed for its own actor even
   * though, with nothing applied any more, a narrow scope no longer reaches the cheque.
   */
  @Test
  void aReversalThatRemovedTheLastApplicationStillReplaysForANarrowScope() {
    var wrong = stored("2000.00");
    wrong.reverse(date, now, now, actor, "Applied to the wrong instalment");
    when(applications.findByTenantIdAndId(tenant, wrong.getId())).thenReturn(Optional.of(wrong));
    cheque.setCreatedBy(UUID.randomUUID());
    lenient()
        .when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    var original = mock(CollectionCommandReceipt.class);
    when(commands.replay(any(), any(), any())).thenReturn(Optional.of(original));
    assertThat(service.reverseApplication(chequeId, wrong.getId(), reversal(), actor))
        .isSameAs(original);
    verify(applications, never()).saveAndFlush(any());
  }

  @Test
  void anAllocationNamesEachInstalmentOnceAndOnlyInstalmentsTheActorMayWrite() {
    allocatable();
    var twice =
        new CollectionRequests.AllocateCheque(
            UUID.randomUUID(),
            0L,
            date,
            now,
            "Buyer asked to use the cheque for this instalment",
            "E-mail from the buyer's accounts team",
            List.of(
                new CollectionRequests.Application(rowId, 0L, new BigDecimal("100.00")),
                new CollectionRequests.Application(rowId, 0L, new BigDecimal("200.00"))));
    when(posting.writableBatch(List.of(rowId, rowId), actor)).thenReturn(List.of(row, row));
    assertThatThrownBy(() -> service.allocate(chequeId, twice, actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("only once per request");
    when(posting.writableBatch(List.of(rowId), actor))
        .thenThrow(
            new com.fabricmanagement.common.infrastructure.web.exception.NotFoundException(
                "Collection obligation not found"));
    assertThatThrownBy(() -> service.allocate(chequeId, allocation("100.00"), actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    verify(applications, never()).save(any());
  }

  @Test
  void anAllocationCannotPrecedeTheChequesRecordedHistoryAndCarriesItsCurrentState() {
    allocatable();
    when(events.latestEffectiveDate(tenant, chequeId)).thenReturn(date.plusDays(1));
    assertThatThrownBy(() -> service.allocate(chequeId, allocation("100.00"), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("recorded state history");
    when(events.latestEffectiveDate(tenant, chequeId)).thenReturn(date);
    cheque.transition(ChequeAction.PRESENT, null, "Presented at the bank");
    service.allocate(chequeId, allocation("100.00"), actor);
    verify(events)
        .save(
            argThat(
                (CollectionChequeEvent event) ->
                    event.getAction().equals("APPLY")
                        && event.getState() == ChequeState.PRESENTED
                        && event.getEvidenceRevision() == 2L));
    assertThat(cheque.getState()).isEqualTo(ChequeState.PRESENTED);
  }

  @Test
  void anApplicationOfAnotherChequeIsNotFound() {
    var foreign =
        CollectionChequeApplication.create(
            UUID.randomUUID(), rowId, new BigDecimal("100.00"), date, now, actor, "Other cheque");
    foreign.setId(UUID.randomUUID());
    when(applications.findByTenantIdAndId(tenant, foreign.getId()))
        .thenReturn(Optional.of(foreign));
    assertThatThrownBy(
            () -> service.reverseApplication(chequeId, foreign.getId(), reversal(), actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
  }

  /** R6: undoing an unlinked cheque's clearing takes the reverse grant over its creator. */
  @Test
  void undoingAnUnlinkedClearingTakesTheReverseGrantOverItsCreator() {
    unlinked();
    cheque.transition(ChequeAction.CLEAR, paymentId, "Bank confirmed clearing");
    cheque.setCreatedBy(UUID.randomUUID());
    when(access.reach(actor, "reverse"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThatThrownBy(() -> service.transition(chequeId, unlinked(ChequeAction.DISHONOUR), actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
    verifyNoInteractions(payments);
    cheque.setCreatedBy(actor);
    service.transition(chequeId, unlinked(ChequeAction.DISHONOUR), actor);
    assertThat(cheque.getState()).isEqualTo(ChequeState.DISHONOURED);
    verify(payments).reverseReleasedInstrument(eq(paymentId), any(), eq(actor));
  }

  /** PAYMENT-PLAN-9 S1: the application is evidence of the receipt, recorded with its own facts. */
  @Test
  void aReceivedApplicationCarriesTheSameEvidenceAsItsReceiptEvent() {
    when(drawers.forReceipt(any()))
        .thenReturn(new CollectionCheque.Drawer(DrawerKind.OWN, null, "Northbridge Cloth"));
    service.receive(receipt(), actor);
    var saved = ArgumentCaptor.forClass(CollectionChequeApplication.class);
    verify(applications).save(saved.capture());
    var event = ArgumentCaptor.forClass(CollectionChequeEvent.class);
    verify(events).saveAndFlush(event.capture());
    var applied = saved.getValue();
    assertThat(event.getValue().getAction()).isEqualTo("RECEIVE");
    assertThat(applied.getAmount()).isEqualByComparingTo("2000.00");
    assertThat(applied.getEffectiveDate()).isEqualTo(event.getValue().getEffectiveDate());
    assertThat(applied.getOccurredAt()).isEqualTo(event.getValue().getOccurredAt());
    assertThat(applied.getActorId()).isEqualTo(event.getValue().getActorId());
    assertThat(applied.getReason()).isEqualTo(event.getValue().getReason());
    assertThat(applied.isReversed()).isFalse();
  }

  /**
   * PAYMENT-PLAN-9 S1: two active rows on one instalment are one obligation. Access and versions
   * are checked once; clearing posts one match for their sum and one payment for the face amount.
   */
  @Test
  void severalActiveApplicationsToOneInstalmentClearAsOneSummedMatch() {
    when(applications.active(tenant, chequeId))
        .thenReturn(List.of(application("2000.00"), application("1000.00")));
    var answer = mock(PaymentDto.class);
    when(answer.id()).thenReturn(paymentId);
    when(payments.createPayment(any())).thenReturn(answer);
    service.transition(chequeId, transition(ChequeAction.CLEAR), actor);
    verify(posting).writableBatch(List.of(rowId), actor);
    verify(obligations).findByTenantIdAndIdIn(tenant, List.of(rowId));
    verify(posting)
        .postChequeBatch(
            eq(paymentId),
            eq(chequeId),
            eq(List.of(row)),
            ArgumentMatchers.<List<BigDecimal>>argThat(
                amounts ->
                    amounts.size() == 1
                        && amounts.getFirst().compareTo(new BigDecimal("3000.00")) == 0),
            eq(date),
            eq(now),
            eq(actor),
            anyString());
    verify(payments)
        .createPayment(argThat(value -> value.amount().compareTo(new BigDecimal("4284.00")) == 0));
    assertThat(cheque.getState()).isEqualTo(ChequeState.CLEARED);
  }

  /** PAYMENT-PLAN-9 S1: a correction may not go below what the cheque currently covers. */
  @Test
  void aCorrectionMayNotGoBelowTheSumOfTheActiveApplications() {
    when(applications.active(tenant, chequeId))
        .thenReturn(List.of(application("2000.00"), application("1000.00")));
    assertThatThrownBy(() -> service.transition(chequeId, correction("2999.99"), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("below its recorded applications");
    assertThat(cheque.getAmount()).isEqualByComparingTo("4284.00");
    service.transition(chequeId, correction("3000.00"), actor);
    assertThat(cheque.getAmount()).isEqualByComparingTo("3000.00");
  }

  private CollectionRequests.Transition correction(String amount) {
    return new CollectionRequests.Transition(
        UUID.randomUUID(),
        0L,
        ChequeAction.CORRECT,
        date,
        now,
        "Customer sent a corrected copy",
        "Corrected instrument scan",
        null,
        new CollectionRequests.Correction(
            new BigDecimal(amount), date, date.plusMonths(3), "NORTHBANK-0042"),
        null);
  }

  private CollectionRequests.Transition transition(ChequeAction action) {
    return new CollectionRequests.Transition(
        UUID.randomUUID(),
        0L,
        action,
        date,
        now,
        "Bank evidence reviewed",
        "Statement and instrument reference verified",
        null,
        null,
        List.of(new CollectionRequests.Version(rowId, 0L)));
  }

  @Test
  void aLaterRecordedStateCannotBeInsertedBeforeAnExistingEffectiveEvent() {
    when(events.latestEffectiveDate(tenant, chequeId)).thenReturn(date.plusDays(1));
    assertThatThrownBy(() -> service.transition(chequeId, transition(ChequeAction.PRESENT), actor))
        .isInstanceOf(CollectionRuleException.class)
        .hasMessageContaining("state history");
    assertThat(cheque.getState()).isEqualTo(ChequeState.RECEIVED);
    verifyNoInteractions(payments);
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void sameDayTransitionsCaptureDistinctExecutionRevisions() {
    service.transition(chequeId, transition(ChequeAction.PRESENT), actor);
    verify(events).saveAndFlush(argThat(e -> e.getEvidenceRevision() == 1L));
    service.transition(chequeId, transition(ChequeAction.RETURN), actor);
    verify(events).saveAndFlush(argThat(e -> e.getEvidenceRevision() == 2L));
  }
}
