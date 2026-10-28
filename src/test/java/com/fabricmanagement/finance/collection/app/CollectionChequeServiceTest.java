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
            "Signed instrument scan");
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
        .when(applications.findByTenantIdAndChequeId(tenant, chequeId))
        .thenReturn(
            List.of(
                CollectionChequeApplication.create(chequeId, rowId, new BigDecimal("4284.00"))));
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
            List.of(new CollectionRequests.Application(rowId, 0L, new BigDecimal("4284.00")))),
        actor);
    verifyNoInteractions(payments);
    verify(posting, never())
        .postChequeBatch(any(), any(), anyList(), anyList(), any(), any(), any(), any());
    verify(events).saveAndFlush(argThat(value -> value.getState() == ChequeState.RECEIVED));
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
