package com.fabricmanagement.finance.collection.app;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one place refunds are opened and their lifecycle recorded, so a collection command, a refund
 * command and a payment void or reversal all see the same state. It depends on repositories only:
 * payment reaches it through a port, and a service that uses {@code PaymentService} here would
 * close a bean cycle. Callers hold the finance tenant lock.
 */
@Component
@RequiredArgsConstructor
public class CollectionRefundLedger {
  private static final String FUNDING_UNDONE =
      "The payment this refund was owed from was reversed, so nothing is owed back";

  private final CollectionRefundRepository refunds;
  private final CollectionRefundEventRepository events;

  /** A refund and where its history leaves it. {@code step} 0 means it has no events yet. */
  public record View(
      CollectionRefund refund,
      RefundState state,
      int step,
      LocalDate lastEffective,
      UUID settlementPaymentId) {}

  public CollectionRefund open(
      CollectionObligation obligation,
      UUID payment,
      BigDecimal amount,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason,
      List<CollectionRefund.ReleasedAllocation> released) {
    return refunds.saveAndFlush(
        CollectionRefund.open(
            obligation, payment, amount, effective, occurred, actor, reason, released));
  }

  public View view(UUID tenant, UUID refundId) {
    var refund = refunds.findByTenantIdAndId(tenant, refundId).orElse(null);
    return refund == null ? null : views(tenant, List.of(refund)).getFirst();
  }

  public List<View> forObligation(UUID tenant, UUID obligation) {
    return views(
        tenant, refunds.findByTenantIdAndObligationIdOrderByCreatedAtAscIdAsc(tenant, obligation));
  }

  public List<View> views(UUID tenant, List<CollectionRefund> rows) {
    if (rows.isEmpty()) return List.of();
    Map<UUID, List<CollectionRefundEvent>> history =
        events
            .findByTenantIdAndRefundIdInOrderByRefundIdAscStepAsc(
                tenant, rows.stream().map(CollectionRefund::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(CollectionRefundEvent::getRefundId));
    return rows.stream()
        .map(row -> replay(row, history.getOrDefault(row.getId(), List.of())))
        .toList();
  }

  /** Money of this inbound payment that is owed back, paid or not: it can no longer be spent. */
  public BigDecimal held(UUID tenant, UUID payment) {
    return views(tenant, refunds.findByTenantIdAndPaymentId(tenant, payment)).stream()
        .filter(view -> view.state() != RefundState.CANCELLED)
        .map(view -> view.refund().getAmount())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Money of this inbound payment that has already been paid back to the customer. */
  public BigDecimal paid(UUID tenant, UUID payment) {
    return views(tenant, refunds.findByTenantIdAndPaymentId(tenant, payment)).stream()
        .filter(view -> view.state() == RefundState.SETTLED)
        .map(view -> view.refund().getAmount())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** What is still owed back per obligation: refunds that are open right now. */
  public Map<UUID, BigDecimal> pendingByObligation(UUID tenant, List<UUID> obligations) {
    if (obligations.isEmpty()) return Map.of();
    Map<UUID, BigDecimal> pending = new HashMap<>();
    for (var view : views(tenant, refunds.findByTenantIdAndObligationIdIn(tenant, obligations)))
      if (view.state() == RefundState.OPEN)
        pending.merge(view.refund().getObligationId(), view.refund().getAmount(), BigDecimal::add);
    return pending;
  }

  /** Refunds an outbound payment currently settles. */
  public List<View> settledBy(UUID tenant, UUID payment) {
    Set<UUID> ids = new HashSet<>();
    for (var event :
        events.findByTenantIdAndPaymentIdAndAction(tenant, payment, RefundAction.SETTLE))
      ids.add(event.getRefundId());
    var rows = new ArrayList<CollectionRefund>();
    for (UUID id : ids) refunds.findByTenantIdAndId(tenant, id).ifPresent(rows::add);
    return views(tenant, rows).stream()
        .filter(
            view ->
                view.state() == RefundState.SETTLED && payment.equals(view.settlementPaymentId()))
        .toList();
  }

  /** Refunds of this inbound payment that nobody has paid yet: still owed, so still open. */
  public List<View> unpaidOf(UUID tenant, UUID payment) {
    return views(tenant, refunds.findByTenantIdAndPaymentId(tenant, payment)).stream()
        .filter(view -> view.state() == RefundState.OPEN)
        .toList();
  }

  /**
   * The latest day reached by the history of the refunds that releasing this payment would move:
   * the ones it settles, or the unpaid ones of it. Releasing it on an earlier day would date a
   * refund event before that refund's own history. Null when it would move none.
   */
  public LocalDate releaseFloor(UUID tenant, UUID payment) {
    return Stream.concat(unpaidOf(tenant, payment).stream(), settledBy(tenant, payment).stream())
        .map(View::lastEffective)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  /**
   * Records the next event; the transition and the date order are checked against the history. A
   * date before the history is refused with the day that works, never moved to a day nobody chose.
   */
  public CollectionRefundEvent append(
      View view,
      RefundAction action,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason,
      UUID payment) {
    view.state().next(action);
    if (effective.isBefore(view.lastEffective()))
      throw CollectionRuleException.refundDateBeforeHistory(view.lastEffective());
    return events.saveAndFlush(
        CollectionRefundEvent.record(
            view.refund().getId(),
            view.step() + 1,
            action,
            effective,
            occurred,
            actor,
            reason,
            payment));
  }

  /**
   * The outbound payment that paid these refunds was voided or reversed, so they are owed again.
   * Returns the obligations whose refunds reopened, so the caller can tell them they changed. The
   * caller checks {@link #releaseFloor} first: a date before a refund's history is refused here.
   *
   * <p>A refund whose inbound payment was itself reversed in the meantime is not owed again: that
   * receipt was taken back, so there is nothing left to give back from it. It is reopened and
   * cancelled on the same day, so its history still shows what happened. {@code fundingUndone} says
   * whether an inbound payment was reversed or voided.
   */
  public List<UUID> reopenSettledBy(
      UUID tenant,
      UUID payment,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason,
      Predicate<UUID> fundingUndone) {
    var reopened = new ArrayList<UUID>();
    for (var view : settledBy(tenant, payment)) {
      var event = append(view, RefundAction.REOPEN, effective, occurred, actor, reason, payment);
      reopened.add(view.refund().getObligationId());
      if (fundingUndone.test(view.refund().getPaymentId()))
        append(
            new View(view.refund(), RefundState.OPEN, event.getStep(), effective, null),
            RefundAction.CANCEL,
            effective,
            occurred,
            actor,
            FUNDING_UNDONE,
            null);
    }
    return List.copyOf(reopened);
  }

  /**
   * The cheque behind this inbound payment did not honour: refunds of it that are still unpaid stop
   * being owed. Returns the obligations they belonged to, so the caller can tell them they changed.
   * The caller checks {@link #releaseFloor} first: a date before a refund's history is refused
   * here.
   */
  public List<UUID> cancelUnpaidOf(
      UUID tenant, UUID payment, LocalDate effective, Instant occurred, UUID actor, String reason) {
    var cancelled = new ArrayList<UUID>();
    for (var view : unpaidOf(tenant, payment)) {
      append(view, RefundAction.CANCEL, effective, occurred, actor, reason, null);
      cancelled.add(view.refund().getObligationId());
    }
    return List.copyOf(cancelled);
  }

  static View replay(CollectionRefund refund, List<CollectionRefundEvent> history) {
    RefundState state = RefundState.OPEN;
    int step = 0;
    LocalDate effective = refund.getEffectiveDate();
    UUID settledBy = null;
    for (var event : history) {
      state = state.next(event.getAction());
      step = event.getStep();
      effective = event.getEffectiveDate();
      settledBy = event.getAction() == RefundAction.SETTLE ? event.getPaymentId() : null;
    }
    return new View(refund, state, step, effective, settledBy);
  }
}
