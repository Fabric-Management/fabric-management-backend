package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionRefund;
import com.fabricmanagement.finance.collection.domain.CollectionRefundEvent;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundAction;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundState;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.CollectionRefundEventRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionRefundRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CollectionRefundLedgerTest {
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
  final UUID obligation = UUID.randomUUID(), payment = UUID.randomUUID();
  final LocalDate opened = LocalDate.of(2026, 10, 10), later = LocalDate.of(2026, 10, 15);
  final Instant now = Instant.parse("2026-10-20T12:00:00Z");
  CollectionRefundRepository refunds;
  CollectionRefundEventRepository events;
  CollectionRefundLedger ledger;
  List<CollectionRefundEvent> history;

  @BeforeEach
  void setup() {
    refunds = mock(CollectionRefundRepository.class);
    events = mock(CollectionRefundEventRepository.class);
    ledger = new CollectionRefundLedger(refunds, events);
    history = new ArrayList<>();
    when(events.findByTenantIdAndRefundIdInOrderByRefundIdAscStepAsc(eq(tenant), anyList()))
        .thenAnswer(call -> history);
    when(events.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
  }

  CollectionRefund refund(UUID id, UUID on, UUID paidFrom, String amount) {
    var row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "GBP");
    row.setId(on);
    var value =
        CollectionRefund.open(
            row, paidFrom, new BigDecimal(amount), opened, now, actor, "Paid", List.of());
    value.setId(id);
    return value;
  }

  CollectionRefundEvent event(UUID refund, int step, RefundAction action, LocalDate on, UUID by) {
    return CollectionRefundEvent.record(refund, step, action, on, now, actor, "Why", by);
  }

  @Test
  void aRefundWithoutEventsIsOpenAtStepZeroAndEffectiveWhenItWasOpened() {
    UUID id = UUID.randomUUID();
    when(refunds.findByTenantIdAndId(tenant, id))
        .thenReturn(Optional.of(refund(id, obligation, payment, "40.00")));

    var view = ledger.view(tenant, id);

    assertThat(view.state()).isEqualTo(RefundState.OPEN);
    assertThat(view.step()).isZero();
    assertThat(view.lastEffective()).isEqualTo(opened);
    assertThat(view.settlementPaymentId()).isNull();
  }

  @Test
  void anUnknownRefundHasNoView() {
    UUID id = UUID.randomUUID();
    when(refunds.findByTenantIdAndId(tenant, id)).thenReturn(Optional.empty());
    assertThat(ledger.view(tenant, id)).isNull();
  }

  @Test
  void theLastEventDecidesStateStepDateAndWhoPaid() {
    UUID id = UUID.randomUUID(), outbound = UUID.randomUUID();
    var value = refund(id, obligation, payment, "40.00");
    history.add(event(id, 1, RefundAction.SETTLE, later, outbound));
    history.add(event(id, 2, RefundAction.REOPEN, later.plusDays(1), outbound));
    history.add(event(id, 3, RefundAction.SETTLE, later.plusDays(2), outbound));

    var view = ledger.views(tenant, List.of(value)).getFirst();

    assertThat(view.state()).isEqualTo(RefundState.SETTLED);
    assertThat(view.step()).isEqualTo(3);
    assertThat(view.lastEffective()).isEqualTo(later.plusDays(2));
    assertThat(view.settlementPaymentId()).isEqualTo(outbound);
  }

  @Test
  void aReopenedRefundIsPaidByNobody() {
    UUID id = UUID.randomUUID(), outbound = UUID.randomUUID();
    history.add(event(id, 1, RefundAction.SETTLE, later, outbound));
    history.add(event(id, 2, RefundAction.REOPEN, later, outbound));

    var view = ledger.views(tenant, List.of(refund(id, obligation, payment, "40.00"))).getFirst();

    assertThat(view.state()).isEqualTo(RefundState.OPEN);
    assertThat(view.settlementPaymentId()).isNull();
  }

  @Test
  void moneyOwedBackStaysHeldWhetherOrNotItIsPaidButNotOnceCancelled() {
    UUID open = UUID.randomUUID(), paid = UUID.randomUUID(), cancelled = UUID.randomUUID();
    when(refunds.findByTenantIdAndPaymentId(tenant, payment))
        .thenReturn(
            List.of(
                refund(open, obligation, payment, "10.00"),
                refund(paid, obligation, payment, "20.00"),
                refund(cancelled, obligation, payment, "40.00")));
    history.add(event(paid, 1, RefundAction.SETTLE, later, UUID.randomUUID()));
    history.add(event(cancelled, 1, RefundAction.CANCEL, later, null));

    assertThat(ledger.held(tenant, payment)).isEqualByComparingTo("30.00");
  }

  @Test
  void aPaymentWithNoRefundsHoldsNothing() {
    when(refunds.findByTenantIdAndPaymentId(tenant, payment)).thenReturn(List.of());
    assertThat(ledger.held(tenant, payment)).isEqualByComparingTo("0");
  }

  @Test
  void onlyOpenRefundsAreStillOwedPerObligation() {
    UUID other = UUID.randomUUID();
    UUID open = UUID.randomUUID(), reopened = UUID.randomUUID(), paid = UUID.randomUUID();
    UUID elsewhere = UUID.randomUUID();
    when(refunds.findByTenantIdAndObligationIdIn(tenant, List.of(obligation, other)))
        .thenReturn(
            List.of(
                refund(open, obligation, payment, "10.00"),
                refund(reopened, obligation, payment, "5.00"),
                refund(paid, obligation, payment, "99.00"),
                refund(elsewhere, other, payment, "7.00")));
    history.add(event(reopened, 1, RefundAction.SETTLE, later, UUID.randomUUID()));
    history.add(event(reopened, 2, RefundAction.REOPEN, later, UUID.randomUUID()));
    history.add(event(paid, 1, RefundAction.SETTLE, later, UUID.randomUUID()));

    var pending = ledger.pendingByObligation(tenant, List.of(obligation, other));

    assertThat(pending.get(obligation)).isEqualByComparingTo("15.00");
    assertThat(pending.get(other)).isEqualByComparingTo("7.00");
  }

  @Test
  void askingAboutNoObligationsReadsNothing() {
    assertThat(ledger.pendingByObligation(tenant, List.of())).isEmpty();
    verify(refunds, never()).findByTenantIdAndObligationIdIn(any(), any());
  }

  @Test
  void appendingRecordsTheNextStepAndRefusesAnImpossibleOne() {
    UUID id = UUID.randomUUID(), outbound = UUID.randomUUID();
    var open = ledger.views(tenant, List.of(refund(id, obligation, payment, "40.00"))).getFirst();

    var saved = ledger.append(open, RefundAction.SETTLE, later, now, actor, "Paid", outbound);

    assertThat(saved.getStep()).isEqualTo(1);
    assertThat(saved.getAction()).isEqualTo(RefundAction.SETTLE);
    assertThat(saved.getPaymentId()).isEqualTo(outbound);
    assertThatThrownBy(
            () -> ledger.append(open, RefundAction.REOPEN, later, now, actor, "No", outbound))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("REFUND_TRANSITION_NOT_ALLOWED"));
  }

  @Test
  void anEventCannotBeDatedBeforeTheRefundsHistory() {
    UUID id = UUID.randomUUID();
    var open = ledger.views(tenant, List.of(refund(id, obligation, payment, "40.00"))).getFirst();

    assertThatThrownBy(
            () ->
                ledger.append(
                    open, RefundAction.CANCEL, opened.minusDays(1), now, actor, "Early", null))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused -> {
              assertThat(refused.getErrorCode()).isEqualTo("REFUND_DATE_BEFORE_HISTORY");
              assertThat(refused.getDetails()).containsEntry("earliestDate", opened.toString());
              assertThat(refused.getArgs()).containsExactly(opened.toString());
            });
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void aRefundEventMayBeDatedOnTheLastDayOfTheHistoryItself() {
    UUID id = UUID.randomUUID();
    var open = ledger.views(tenant, List.of(refund(id, obligation, payment, "40.00"))).getFirst();

    var saved = ledger.append(open, RefundAction.CANCEL, opened, now, actor, "Same day", null);

    assertThat(saved.getEffectiveDate()).isEqualTo(opened);
  }

  @Test
  void reopeningTouchesOnlyRefundsThatPaymentSettlesOnTheDateItWasGiven() {
    UUID id = UUID.randomUUID(), outbound = UUID.randomUUID();
    var value = refund(id, obligation, payment, "40.00");
    var settled = event(id, 1, RefundAction.SETTLE, later, outbound);
    history.add(settled);
    when(events.findByTenantIdAndPaymentIdAndAction(tenant, outbound, RefundAction.SETTLE))
        .thenReturn(List.of(settled));
    when(refunds.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(value));

    var reopened =
        ledger.reopenSettledBy(
            tenant,
            outbound,
            later.plusDays(1),
            now,
            actor,
            "Settling payment reversed",
            source -> false);

    assertThat(reopened).containsExactly(obligation);
    var saved = ArgumentCaptor.forClass(CollectionRefundEvent.class);
    verify(events).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getAction()).isEqualTo(RefundAction.REOPEN);
    assertThat(saved.getValue().getStep()).isEqualTo(2);
    assertThat(saved.getValue().getEffectiveDate()).isEqualTo(later.plusDays(1));
    assertThat(saved.getValue().getPaymentId()).isEqualTo(outbound);
  }

  @Test
  void aRefundWhoseReceiptWasTakenBackIsReopenedAndCancelledOnTheSameDay() {
    UUID id = UUID.randomUUID(), outbound = UUID.randomUUID();
    var value = refund(id, obligation, payment, "40.00");
    var settled = event(id, 1, RefundAction.SETTLE, later, outbound);
    history.add(settled);
    when(events.findByTenantIdAndPaymentIdAndAction(tenant, outbound, RefundAction.SETTLE))
        .thenReturn(List.of(settled));
    when(refunds.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(value));

    var reopened =
        ledger.reopenSettledBy(
            tenant,
            outbound,
            later.plusDays(1),
            now,
            actor,
            "Refund payment reversed",
            source -> source.equals(payment));

    assertThat(reopened).containsExactly(obligation);
    var saved = ArgumentCaptor.forClass(CollectionRefundEvent.class);
    verify(events, times(2)).saveAndFlush(saved.capture());
    var reopen = saved.getAllValues().get(0);
    var cancel = saved.getAllValues().get(1);
    assertThat(reopen.getAction()).isEqualTo(RefundAction.REOPEN);
    assertThat(reopen.getStep()).isEqualTo(2);
    assertThat(cancel.getAction()).isEqualTo(RefundAction.CANCEL);
    assertThat(cancel.getStep()).isEqualTo(3);
    assertThat(cancel.getEffectiveDate()).isEqualTo(later.plusDays(1));
    assertThat(cancel.getPaymentId()).isNull();
    assertThat(cancel.getReason()).contains("reversed");
  }

  @Test
  void aRefundWhoseReceiptStandsIsOnlyReopened() {
    UUID id = UUID.randomUUID(), outbound = UUID.randomUUID();
    var value = refund(id, obligation, payment, "40.00");
    var settled = event(id, 1, RefundAction.SETTLE, later, outbound);
    history.add(settled);
    when(events.findByTenantIdAndPaymentIdAndAction(tenant, outbound, RefundAction.SETTLE))
        .thenReturn(List.of(settled));
    when(refunds.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(value));

    ledger.reopenSettledBy(tenant, outbound, later, now, actor, "Reversed", source -> false);

    var saved = ArgumentCaptor.forClass(CollectionRefundEvent.class);
    verify(events).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getAction()).isEqualTo(RefundAction.REOPEN);
  }

  @Test
  void whatWasPaidBackIsOnlyTheRefundsThatAreSettled() {
    UUID openId = UUID.randomUUID(), paidId = UUID.randomUUID(), cancelledId = UUID.randomUUID();
    UUID outbound = UUID.randomUUID();
    var unpaid = refund(openId, obligation, payment, "40.00");
    var paid = refund(paidId, obligation, payment, "25.00");
    var dropped = refund(cancelledId, obligation, payment, "10.00");
    history.add(event(paidId, 1, RefundAction.SETTLE, later, outbound));
    history.add(event(cancelledId, 1, RefundAction.CANCEL, later, null));
    when(refunds.findByTenantIdAndPaymentId(tenant, payment))
        .thenReturn(List.of(unpaid, paid, dropped));

    assertThat(ledger.paid(tenant, payment)).isEqualByComparingTo("25.00");
    assertThat(ledger.held(tenant, payment)).isEqualByComparingTo("65.00");
  }

  /** The event is never moved to a day nobody chose: the date is refused and nothing is saved. */
  @Test
  void reopeningOnADayBeforeTheRefundsHistoryIsRefusedNotMoved() {
    UUID id = UUID.randomUUID(), outbound = UUID.randomUUID();
    var value = refund(id, obligation, payment, "40.00");
    var settled = event(id, 1, RefundAction.SETTLE, later, outbound);
    history.add(settled);
    when(events.findByTenantIdAndPaymentIdAndAction(tenant, outbound, RefundAction.SETTLE))
        .thenReturn(List.of(settled));
    when(refunds.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(value));

    assertThatThrownBy(
            () ->
                ledger.reopenSettledBy(
                    tenant, outbound, opened, now, actor, "Reversed early", source -> false))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused -> {
              assertThat(refused.getErrorCode()).isEqualTo("REFUND_DATE_BEFORE_HISTORY");
              assertThat(refused.getArgs()).containsExactly(later.toString());
            });
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void aPaymentThatSettledNothingReopensNothing() {
    UUID outbound = UUID.randomUUID();
    when(events.findByTenantIdAndPaymentIdAndAction(tenant, outbound, RefundAction.SETTLE))
        .thenReturn(List.of());

    assertThat(
            ledger.reopenSettledBy(tenant, outbound, later, now, actor, "Voided", source -> false))
        .isEmpty();
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void aRefundLaterPaidByAnotherPaymentIsNotReopenedByTheFirstOnesReversal() {
    UUID id = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
    var value = refund(id, obligation, payment, "40.00");
    var firstSettlement = event(id, 1, RefundAction.SETTLE, later, first);
    history.add(firstSettlement);
    history.add(event(id, 2, RefundAction.REOPEN, later, first));
    history.add(event(id, 3, RefundAction.SETTLE, later, second));
    when(events.findByTenantIdAndPaymentIdAndAction(tenant, first, RefundAction.SETTLE))
        .thenReturn(List.of(firstSettlement));
    when(refunds.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(value));

    assertThat(
            ledger.reopenSettledBy(tenant, first, later, now, actor, "Reversed", source -> false))
        .isEmpty();
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void aFailedChequePaymentCancelsItsUnpaidRefundsAndLeavesPaidOnesAlone() {
    UUID openId = UUID.randomUUID(), paidId = UUID.randomUUID(), outbound = UUID.randomUUID();
    UUID otherObligation = UUID.randomUUID();
    var unpaid = refund(openId, obligation, payment, "40.00");
    var paid = refund(paidId, otherObligation, payment, "25.00");
    history.add(event(paidId, 1, RefundAction.SETTLE, later, outbound));
    when(refunds.findByTenantIdAndPaymentId(tenant, payment)).thenReturn(List.of(unpaid, paid));

    var cancelled = ledger.cancelUnpaidOf(tenant, payment, later, now, actor, "Cheque returned");

    assertThat(cancelled).containsExactly(obligation);
    var saved = ArgumentCaptor.forClass(CollectionRefundEvent.class);
    verify(events).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getRefundId()).isEqualTo(openId);
    assertThat(saved.getValue().getAction()).isEqualTo(RefundAction.CANCEL);
    assertThat(saved.getValue().getStep()).isEqualTo(1);
    assertThat(saved.getValue().getEffectiveDate()).isEqualTo(later);
  }

  @Test
  void aFailedChequeOnADayBeforeTheRefundWasOwedIsRefusedNotMoved() {
    UUID openId = UUID.randomUUID();
    var unpaid = refund(openId, obligation, payment, "40.00");
    when(refunds.findByTenantIdAndPaymentId(tenant, payment)).thenReturn(List.of(unpaid));

    assertThatThrownBy(
            () ->
                ledger.cancelUnpaidOf(
                    tenant, payment, opened.minusDays(1), now, actor, "Cheque returned"))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused -> {
              assertThat(refused.getErrorCode()).isEqualTo("REFUND_DATE_BEFORE_HISTORY");
              assertThat(refused.getArgs()).containsExactly(opened.toString());
            });
    verify(events, never()).saveAndFlush(any());
  }

  @Test
  void releasingAPaymentIsBoundedByTheLatestHistoryOfTheUnpaidRefundsItHolds() {
    UUID openId = UUID.randomUUID(), reopenedId = UUID.randomUUID(), paidId = UUID.randomUUID();
    var unpaid = refund(openId, obligation, payment, "10.00");
    var reopened = refund(reopenedId, obligation, payment, "5.00");
    var paid = refund(paidId, obligation, payment, "20.00");
    when(refunds.findByTenantIdAndPaymentId(tenant, payment))
        .thenReturn(List.of(unpaid, reopened, paid));
    history.add(event(reopenedId, 1, RefundAction.SETTLE, later, UUID.randomUUID()));
    history.add(event(reopenedId, 2, RefundAction.REOPEN, later.plusDays(3), UUID.randomUUID()));
    // A paid refund stays as recorded when the cheque fails, so it does not bound the date.
    history.add(event(paidId, 1, RefundAction.SETTLE, later.plusDays(9), UUID.randomUUID()));

    assertThat(ledger.releaseFloor(tenant, payment)).isEqualTo(later.plusDays(3));
  }

  @Test
  void releasingAnOutboundPaymentIsBoundedByTheDayItSettledItsRefund() {
    UUID id = UUID.randomUUID(), outbound = UUID.randomUUID();
    var settled = event(id, 1, RefundAction.SETTLE, later, outbound);
    history.add(settled);
    when(events.findByTenantIdAndPaymentIdAndAction(tenant, outbound, RefundAction.SETTLE))
        .thenReturn(List.of(settled));
    when(refunds.findByTenantIdAndId(tenant, id))
        .thenReturn(Optional.of(refund(id, obligation, payment, "40.00")));

    assertThat(ledger.releaseFloor(tenant, outbound)).isEqualTo(later);
  }

  @Test
  void aPaymentThatMovesNoRefundHasNoFloor() {
    when(refunds.findByTenantIdAndPaymentId(tenant, payment)).thenReturn(List.of());

    assertThat(ledger.releaseFloor(tenant, payment)).isNull();
  }

  @Test
  void aPaymentThatHoldsNoRefundCancelsNothing() {
    when(refunds.findByTenantIdAndPaymentId(tenant, payment)).thenReturn(List.of());

    assertThat(ledger.cancelUnpaidOf(tenant, payment, later, now, actor, "Returned")).isEmpty();
    verify(events, never()).saveAndFlush(any());
  }
}
