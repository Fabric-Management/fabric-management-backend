package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort.*;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.*;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionProjectionServiceTest {
  @Mock CollectionSourcePort source;
  @Mock CollectionObligationRepository obligations;
  @Mock CollectionProjectionRepository projections;
  @Mock CollectionExpectationRepository expectations;
  @Mock CollectionCommandStore commands;
  @Mock CollectionEvents events;
  @Mock CollectionSourceObservationRepository observations;
  @InjectMocks CollectionProjectionService service;
  UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      order = UUID.randomUUID(),
      partner = UUID.randomUUID(),
      plan = UUID.randomUUID(),
      revision = UUID.randomUUID(),
      logical = UUID.randomUUID();
  Instant now = Instant.parse("2026-10-20T12:00:00Z");

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(actor);
    lenient().when(commands.now()).thenReturn(now);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void onlyAnOperationallyAcceptedSourceCreatesExpectations() {
    when(source.find(tenant, order)).thenReturn(Optional.empty());
    service.synchronize(order);
    verify(obligations, never()).saveAndFlush(any());
    verifyNoInteractions(expectations);
    assertThat(TenantContext.getCurrentUserId()).isEqualTo(actor);
  }

  @Test
  void aReaderThatCanLiveWithTheLastProjectionSkipsABusyOrderWithoutAnyReadOrError() {
    when(source.tryHold(tenant, order)).thenReturn(false);
    assertThat(service.synchronizeIfFree(order)).isFalse();
    verify(source, never()).find(any(), any());
    verifyNoInteractions(obligations, expectations, observations);
    verify(projections, never()).saveAndFlush(any());
  }

  @Test
  void aReaderRefreshesTheProjectionWhenNoWriterHoldsTheOrder() {
    when(source.tryHold(tenant, order)).thenReturn(true);
    when(source.find(tenant, order)).thenReturn(Optional.empty());
    assertThat(service.synchronizeIfFree(order)).isTrue();
    verify(source).find(tenant, order);
    assertThat(TenantContext.getCurrentUserId()).isEqualTo(actor);
  }

  @Test
  void ordersFoundUnchangedOnlyHaveTheirCheckTimeMovedUnderTheTenantLock() {
    var orders = List.of(order, UUID.randomUUID());
    var listed = now.plusSeconds(30);
    service.confirmUnchanged(orders, listed);
    var steps = inOrder(commands, projections);
    steps.verify(commands).lock();
    steps.verify(projections).confirm(tenant, orders, listed);
    verifyNoInteractions(source, obligations, expectations, observations);
  }

  @Test
  void noOrdersMeansNoLockAndNoWrite() {
    service.confirmUnchanged(List.of(), now);
    verifyNoInteractions(commands, projections);
  }

  @Test
  void aBusySourceOrderIsAConflictTheCallerCanRetryAndTheActorIsRestored() {
    when(source.find(tenant, order)).thenThrow(new SourceBusyException(order));
    assertThatThrownBy(() -> service.synchronize(order))
        .isInstanceOfSatisfying(
            com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException.class,
            busy -> {
              assertThat(busy.getErrorCode()).isEqualTo("COLLECTION_SOURCE_BUSY");
              assertThat(busy.getHttpStatus()).isEqualTo(409);
            });
    verifyNoInteractions(expectations);
    verify(projections, never()).saveAndFlush(any());
    assertThat(TenantContext.getCurrentUserId()).isEqualTo(actor);
  }

  @Test
  void repeatedOrOldEventsUseTheCurrentSourceAndPreserveLogicalIdentity() {
    var current = CollectionObligation.create(order, plan, logical, partner, "GBP");
    current.setId(UUID.randomUUID());
    current.revise(
        revision,
        new BigDecimal("4284.00"),
        Kind.INSTALMENT,
        Method.CHEQUE,
        LocalDate.of(2026, 10, 30),
        false,
        LocalDate.of(2027, 7, 20),
        now,
        false);
    when(obligations.findByTenantIdAndOrderId(tenant, order)).thenReturn(List.of(current));
    when(source.find(tenant, order))
        .thenReturn(Optional.of(agreement(revision, false, new BigDecimal("4284.00"))));
    service.synchronize(order);
    service.synchronize(order);
    verify(obligations, never()).saveAndFlush(any());
    verify(expectations, never()).saveAndFlush(any());
    UUID next = UUID.randomUUID();
    when(source.find(tenant, order))
        .thenReturn(Optional.of(agreement(next, false, new BigDecimal("3000.00"))));
    when(obligations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    service.synchronize(order);
    assertThat(current.getId()).isNotNull();
    assertThat(current.getLogicalId()).isEqualTo(logical);
    assertThat(current.getAmount()).isEqualByComparingTo("3000.00");
    verify(expectations).saveAndFlush(argThat(value -> value.getRevisionId().equals(next)));
    // Projection never rewrites payment matches when an accepted amount is reduced.
  }

  @Test
  void cancellationRetainsTheExpectationAndItsReceiptIdentity() {
    var current = CollectionObligation.create(order, plan, logical, partner, "GBP");
    current.setId(UUID.randomUUID());
    current.revise(
        revision,
        new BigDecimal("4284.00"),
        Kind.INSTALMENT,
        Method.CHEQUE,
        null,
        true,
        LocalDate.of(2027, 7, 20),
        now,
        false);
    when(obligations.findByTenantIdAndOrderId(tenant, order)).thenReturn(List.of(current));
    when(source.find(tenant, order))
        .thenReturn(Optional.of(agreement(revision, true, new BigDecimal("4284.00"))));
    when(obligations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    when(expectations.recordedObligations(tenant, revision, List.of(current.getId())))
        .thenReturn(Set.of(current.getId()));
    service.synchronize(order);
    assertThat(current.isCancelled()).isTrue();
    assertThat(current.getAmount()).isEqualByComparingTo("4284.00");
  }

  private Agreement agreement(UUID revision, boolean cancelled, BigDecimal amount) {
    return new Agreement(
        order,
        partner,
        plan,
        revision,
        3,
        UUID.randomUUID(),
        now,
        now,
        cancelled,
        false,
        List.of(
            new Expectation(
                logical,
                "GBP",
                amount,
                Kind.INSTALMENT,
                Method.CHEQUE,
                LocalDate.of(2026, 10, 30),
                false,
                LocalDate.of(2027, 7, 20))),
        null,
        1L);
  }

  @Test
  void theProjectionRecordsTheOrderVersionItWasBuiltFromSoUnchangedOrdersCanBeSkipped() {
    var base = agreement(revision, false, new BigDecimal("4284.00"));
    var versioned =
        new Agreement(
            order,
            partner,
            plan,
            revision,
            3,
            base.approvalId(),
            now,
            now,
            false,
            false,
            base.rows(),
            null,
            7L);
    when(source.find(tenant, order)).thenReturn(Optional.of(versioned));
    when(obligations.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              CollectionObligation row = call.getArgument(0);
              row.setId(UUID.randomUUID());
              return row;
            });
    service.synchronize(order);
    var saved = ArgumentCaptor.forClass(CollectionProjection.class);
    verify(projections).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getSourceOrderVersion()).isEqualTo(7L);
    assertThat(saved.getValue().getSourceVersion()).isEqualTo(3L);
  }

  @Test
  void firstDiscoveryAfterCancellationRetainsBothKnownEffectiveTimes() {
    var base = agreement(revision, true, new BigDecimal("4284.00"));
    var cancelledAt = now.plusSeconds(60);
    var accepted =
        new Agreement(
            order,
            partner,
            plan,
            revision,
            3,
            base.approvalId(),
            now,
            now,
            true,
            false,
            base.rows(),
            cancelledAt,
            1L);
    when(commands.now()).thenReturn(now.plusSeconds(120));
    when(source.find(tenant, order)).thenReturn(Optional.of(accepted));
    when(obligations.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              CollectionObligation row = call.getArgument(0);
              row.setId(UUID.randomUUID());
              return row;
            });
    service.synchronize(order);
    var history = ArgumentCaptor.forClass(CollectionSourceObservation.class);
    verify(observations, times(2)).saveAndFlush(history.capture());
    assertThat(history.getAllValues())
        .extracting(CollectionSourceObservation::getEffectiveAt)
        .containsExactly(now, cancelledAt);
    assertThat(history.getAllValues().getFirst().isCancelled()).isFalse();
    assertThat(history.getAllValues().getFirst().getActiveLogicalIds()).containsExactly(logical);
    assertThat(history.getAllValues().getLast().isCancelled()).isTrue();
  }

  @Test
  void aScreenThatCannotTakeTheTenantLockAtOnceWaitsForNothingAndReadsNothing() {
    when(commands.tryLock()).thenReturn(false);

    assertThat(service.refreshIfFree(order)).isEqualTo(CollectionProjectionService.Refresh.BUSY);

    verify(commands, never()).lock();
    verifyNoInteractions(source, obligations, expectations, observations);
    verify(projections, never()).saveAndFlush(any());
  }

  @Test
  void aScreenSkipsAnOrderAWriterHoldsAndReadsNothingOfIt() {
    when(commands.tryLock()).thenReturn(true);
    when(source.tryHold(tenant, order)).thenReturn(false);

    assertThat(service.refreshIfFree(order)).isEqualTo(CollectionProjectionService.Refresh.BUSY);

    verify(source, never()).find(any(), any());
    verifyNoInteractions(obligations, expectations, observations);
  }

  @Test
  void aScreenRefreshesWhenBothTheTenantAndTheOrderAreFree() {
    when(commands.tryLock()).thenReturn(true);
    when(source.tryHold(tenant, order)).thenReturn(true);
    when(source.find(tenant, order)).thenReturn(Optional.empty());

    assertThat(service.refreshIfFree(order))
        .isEqualTo(CollectionProjectionService.Refresh.REFRESHED);

    assertThat(TenantContext.getCurrentUserId()).isEqualTo(actor);
  }

  @Test
  void aWriterThatWinsTheOrderAfterTheCheckIsBusyNotAnError() {
    when(commands.tryLock()).thenReturn(true);
    when(source.tryHold(tenant, order)).thenReturn(true);
    when(source.find(tenant, order)).thenThrow(new SourceBusyException(order));

    assertThat(service.refreshIfFree(order)).isEqualTo(CollectionProjectionService.Refresh.BUSY);

    assertThat(TenantContext.getCurrentUserId()).isEqualTo(actor);
  }

  @Test
  void aSourceWithoutItsProofIsAnAnswerForAScreenAndStillAnErrorForTheBackgroundRebuild() {
    when(commands.tryLock()).thenReturn(true);
    when(source.tryHold(tenant, order)).thenReturn(true);
    when(source.find(tenant, order))
        .thenReturn(Optional.of(agreementOf(null, List.of(row(logical, "GBP", "100.00")))));

    assertThat(service.refreshIfFree(order))
        .isEqualTo(CollectionProjectionService.Refresh.UNREADABLE);
    assertThatThrownBy(() -> service.synchronize(order))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused ->
                assertThat(refused.getErrorCode()).isEqualTo("COLLECTION_SOURCE_INCOMPLETE"));

    verify(projections, never()).saveAndFlush(any());
    assertThat(TenantContext.getCurrentUserId()).isEqualTo(actor);
  }

  @Test
  void aRefusedSourceLeavesNothingHalfRebuiltEvenWhenAnEarlierRowWouldHaveBeenWritten() {
    var current = existing(logical, "GBP");
    when(commands.tryLock()).thenReturn(true);
    when(source.tryHold(tenant, order)).thenReturn(true);
    when(obligations.findByTenantIdAndOrderId(tenant, order)).thenReturn(List.of(current));
    // The first row would be revised and the second repeats its identity.
    when(source.find(tenant, order))
        .thenReturn(
            Optional.of(
                agreementOf(
                    UUID.randomUUID(),
                    List.of(row(logical, "GBP", "100.00"), row(logical, "GBP", "200.00")))));

    assertThat(service.refreshIfFree(order))
        .isEqualTo(CollectionProjectionService.Refresh.UNREADABLE);

    assertThat(current.getAmount()).isEqualByComparingTo("4284.00");
    verify(obligations, never()).saveAndFlush(any());
    verifyNoInteractions(expectations, observations, events);
    verify(projections, never()).saveAndFlush(any());
  }

  @Test
  void anObligationThatWouldChangeCurrencyIsRefusedBeforeAnyRowIsWritten() {
    var current = existing(logical, "GBP");
    when(commands.tryLock()).thenReturn(true);
    when(source.tryHold(tenant, order)).thenReturn(true);
    when(obligations.findByTenantIdAndOrderId(tenant, order)).thenReturn(List.of(current));
    // A new first row would be created before the second one is found to change currency.
    when(source.find(tenant, order))
        .thenReturn(
            Optional.of(
                agreementOf(
                    UUID.randomUUID(),
                    List.of(
                        row(UUID.randomUUID(), "GBP", "50.00"), row(logical, "USD", "100.00")))));

    assertThat(service.refreshIfFree(order))
        .isEqualTo(CollectionProjectionService.Refresh.UNREADABLE);
    assertThatThrownBy(() -> service.synchronize(order))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused ->
                assertThat(refused.getErrorCode()).isEqualTo("COLLECTION_SOURCE_IDENTITY_CHANGED"));

    verify(obligations, never()).saveAndFlush(any());
    verifyNoInteractions(expectations, observations, events);
  }

  @Test
  void aFailureThatIsNotAboutTheSourceBeingReadableIsStillAnError() {
    when(commands.tryLock()).thenReturn(true);
    when(source.tryHold(tenant, order)).thenReturn(true);
    when(source.find(tenant, order))
        .thenThrow(CollectionRuleException.conflict("COLLECTION_OTHER", "something else"));

    assertThatThrownBy(() -> service.refreshIfFree(order))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("COLLECTION_OTHER"));
  }

  @Test
  void theBackgroundReaderStillTakesTheTenantLockBeforeItLooksAtTheOrder() {
    when(source.tryHold(tenant, order)).thenReturn(false);

    assertThat(service.synchronizeIfFree(order)).isFalse();

    var sequence = inOrder(commands, source);
    sequence.verify(commands).lock();
    sequence.verify(source).tryHold(tenant, order);
    verify(commands, never()).tryLock();
  }

  private CollectionObligation existing(UUID logicalId, String currency) {
    var current = CollectionObligation.create(order, plan, logicalId, partner, currency);
    current.setId(UUID.randomUUID());
    current.revise(
        revision,
        new BigDecimal("4284.00"),
        Kind.INSTALMENT,
        Method.BANK_TRANSFER,
        LocalDate.of(2026, 10, 30),
        false,
        null,
        now,
        false);
    return current;
  }

  private Expectation row(UUID logicalId, String currency, String amount) {
    return new Expectation(
        logicalId,
        currency,
        new BigDecimal(amount),
        Kind.INSTALMENT,
        Method.BANK_TRANSFER,
        LocalDate.of(2026, 10, 30),
        false,
        null);
  }

  private Agreement agreementOf(UUID approval, List<Expectation> rows) {
    return new Agreement(
        order, partner, plan, revision, 3, approval, now, now, false, false, rows, null, 1L);
  }
}
