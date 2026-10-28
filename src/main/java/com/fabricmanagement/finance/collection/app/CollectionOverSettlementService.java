package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionAmounts;
import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionPaymentMatch;
import com.fabricmanagement.finance.collection.domain.CollectionRefund.ReleasedAllocation;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.OverSettlementDecision;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash recorded above what an expectation agreed is neither lost nor settled until finance says
 * what it is. This is the command that says so: the surplus is moved to another instalment, kept on
 * account as an advance, or owed back as a refund. Evidence is only ever added: the matches that
 * held the surplus are reversed and, where they held more, the rest is matched again.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CollectionOverSettlementService {
  private static final String REMAINDER = "Remainder after over-settlement resolution: ";

  private final CollectionCommandStore commands;
  private final CollectionPostingService posting;
  private final CollectionObligationRepository obligations;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionRefundLedger refunds;
  private final CollectionEvents events;
  private final FinancialPeriodGuard periods;
  private final PaymentService paymentService;

  /** The part of a match that the resolution takes; the rest of the match stays where it is. */
  private record Slice(CollectionPaymentMatch match, BigDecimal take) {}

  public CollectionCommandReceipt resolve(
      UUID obligationId, CollectionRequests.ResolveOverSettlement request, UUID actor) {
    commands.validate(request);
    var decision = request.decision();
    boolean reapply = decision == OverSettlementDecision.REAPPLY;
    boolean refund = decision == OverSettlementDecision.REFUND;
    requireShape(request, reapply);
    commands.lock();
    var row = posting.writable(obligationId, actor);
    // Owing cash back undoes a receipt as surely as reversing its payment does: same grant.
    if (refund) posting.requireReverse(row, actor);
    String hash = commands.hash("RESOLVE_OVER_SETTLEMENT", obligationId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    // Moving money onto another instalment needs the agreement itself to be current. Giving back or
    // parking cash that is already recorded must work even when the agreement is gone, but never on
    // a plan that has moved since it was read: the surplus is computed from the current amount.
    if (reapply) posting.fresh(row);
    else posting.refresh(row);
    commands.version(request.expectedVersion(), row.getVersion());
    commands.time(request.effectiveDate(), request.occurredAt());

    BigDecimal amount = CollectionAmounts.positive(request.amount(), row.getCurrency());
    BigDecimal surplus =
        CollectionAmounts.surplus(row.getAmount(), row.isCancelled(), posting.settled(row.getId()));
    if (surplus.signum() == 0)
      throw CollectionRuleException.conflict(
          "OVER_SETTLEMENT_NOTHING_TO_RESOLVE", "This expectation has no cash above its amount");
    if (amount.compareTo(surplus) > 0)
      throw CollectionRuleException.conflict(
          "OVER_SETTLEMENT_EXCEEDS_SURPLUS",
          "Only " + surplus.toPlainString() + " is recorded above the agreed amount");
    var target = reapply ? target(row, request, actor, amount) : null;
    periods.assertPostingAllowed(TenantContext.requireTenantId(), request.effectiveDate());
    var slices = slices(row, amount, decision, request.effectiveDate());
    var perPayment = new LinkedHashMap<UUID, BigDecimal>();
    for (var slice : slices)
      perPayment.merge(slice.match().getPaymentId(), slice.take(), BigDecimal::add);
    var released =
        refund
            ? makeRoom(perPayment, Boolean.TRUE.equals(request.releaseInvoiceAllocations()))
            : Map.<UUID, List<ReleasedAllocation>>of();

    var ids = new ArrayList<UUID>();
    for (var slice : slices) {
      var match = slice.match();
      posting.reverseMatch(
          match, request.effectiveDate(), request.occurredAt(), actor, request.reason());
      ids.add(match.getId());
      BigDecimal rest = match.getAmount().subtract(slice.take());
      if (rest.signum() > 0)
        ids.add(again(row, match, match.getChequeId(), rest, request, actor, REMAINDER).getId());
      if (target != null) {
        posting.requirePayment(posting.payment(match.getPaymentId()), target);
        ids.add(again(target, match, null, slice.take(), request, actor, "").getId());
      }
    }
    if (refund)
      perPayment.forEach(
          (payment, part) ->
              refunds.open(
                  row,
                  payment,
                  part,
                  request.effectiveDate(),
                  request.occurredAt(),
                  actor,
                  request.reason(),
                  released.getOrDefault(payment, List.of())));

    changed(row);
    if (target != null) {
      changed(target);
      if (posting.settled(target.getId()).compareTo(target.getAmount()) > 0)
        throw CollectionRuleException.conflict(
            "COLLECTION_OVERMATCH", "Match exceeds recorded outstanding");
    }
    UUID payment = perPayment.size() == 1 ? perPayment.keySet().iterator().next() : null;
    return posting.receipt(request.operationId(), actor, hash, row, payment, ids);
  }

  /** REAPPLY names a destination; the other decisions never do. */
  private static void requireShape(
      CollectionRequests.ResolveOverSettlement request, boolean reapply) {
    boolean named = request.targetObligationId() != null || request.targetExpectedVersion() != null;
    if (reapply
        && (request.targetObligationId() == null || request.targetExpectedVersion() == null))
      throw CollectionRuleException.invalid(
          "Moving a surplus needs the instalment it moves to and that instalment's version");
    if (!reapply && named)
      throw CollectionRuleException.invalid(
          "Only moving a surplus names another instalment; leave it out for this decision");
    if (Boolean.TRUE.equals(request.releaseInvoiceAllocations())
        && request.decision() != OverSettlementDecision.REFUND)
      throw CollectionRuleException.invalid(
          "Invoice allocations are released only to make room for a refund");
  }

  /**
   * A refund takes its money from the part of the payment that no invoice has been allocated: the
   * same money an allocation uses, so counting it twice would show the invoice paid while the
   * customer also gets the cash back. Without the sender's explicit say-so the refund is refused
   * and the invoices are named; with it, the newest allocations are released until the refund fits
   * and each release is recorded on the refund.
   */
  private Map<UUID, List<ReleasedAllocation>> makeRoom(
      Map<UUID, BigDecimal> perPayment, boolean release) {
    var tenant = TenantContext.requireTenantId();
    var released = new HashMap<UUID, List<ReleasedAllocation>>();
    for (var entry : perPayment.entrySet()) {
      var payment = posting.payment(entry.getKey());
      BigDecimal held = refunds.held(tenant, payment.getId());
      BigDecimal needed = entry.getValue();
      var undone = new ArrayList<ReleasedAllocation>();
      for (var allocation : newestFirst(payment)) {
        if (room(payment, held).compareTo(needed) >= 0) break;
        if (!release) throw exceedsUnallocated(payment, held, needed);
        release(payment, allocation);
        undone.add(
            new ReleasedAllocation(
                allocation.getId(), allocation.getInvoiceId(), money(allocation.getAmount())));
      }
      if (room(payment, held).compareTo(needed) < 0)
        throw exceedsUnallocated(payment, held, needed);
      if (!undone.isEmpty()) released.put(payment.getId(), List.copyOf(undone));
    }
    return released;
  }

  /**
   * Deallocating posts on the payment's own day, which can be closed even though the refund's day
   * is open; that is said as a rule of this refund rather than as a generic closed-period error.
   */
  private void release(Payment payment, PaymentAllocation allocation) {
    try {
      paymentService.deallocatePayment(payment.getId(), allocation.getId());
    } catch (ClosedFinancialPeriodException closed) {
      throw CollectionRuleException.refundPaymentPeriodClosed(payment.getPaymentDate());
    }
  }

  private static String money(com.fabricmanagement.common.util.Money value) {
    return CollectionAmounts.text(value.getAmount(), value.getCurrency().getCurrencyCode());
  }

  private static BigDecimal room(Payment payment, BigDecimal held) {
    return payment.getUnallocatedAmount().getAmount().subtract(held);
  }

  private static List<PaymentAllocation> newestFirst(Payment payment) {
    return payment.getAllocations().stream()
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .sorted(Comparator.comparing(PaymentAllocation::getAllocatedAt).reversed())
        .toList();
  }

  private static CollectionRuleException exceedsUnallocated(
      Payment payment, BigDecimal held, BigDecimal needed) {
    var invoices =
        newestFirst(payment).stream()
            .map(
                value ->
                    Map.of(
                        "allocationId", value.getId().toString(),
                        "invoiceId", value.getInvoiceId().toString(),
                        "amount", money(value.getAmount())))
            .toList();
    var failure =
        CollectionRuleException.conflict(
            "REFUND_EXCEEDS_UNALLOCATED",
            "Only "
                + CollectionAmounts.text(
                    room(payment, held).max(BigDecimal.ZERO), payment.getCurrency())
                + " of this payment is not allocated to invoices or set aside for another refund;"
                + " refunding "
                + CollectionAmounts.text(needed, payment.getCurrency())
                + " would count the same money twice");
    failure.withDetail("paymentId", payment.getId().toString());
    failure.withDetail("allocations", invoices);
    return failure;
  }

  private CollectionObligation target(
      CollectionObligation source,
      CollectionRequests.ResolveOverSettlement request,
      UUID actor,
      BigDecimal amount) {
    var target = posting.writable(request.targetObligationId(), actor);
    if (target.getId().equals(source.getId())
        || !target.getOrderId().equals(source.getOrderId())
        || !target.getPartnerId().equals(source.getPartnerId())
        || !target.getCurrency().equals(source.getCurrency()))
      throw CollectionRuleException.conflict(
          "OVER_SETTLEMENT_TARGET_INCOMPATIBLE",
          "Choose another instalment of the same order, partner and currency");
    commands.version(request.targetExpectedVersion(), target.getVersion());
    target.requireMatchable(posting.settled(target.getId()), amount);
    posting.requireRoomBesideCheques(target, amount);
    return target;
  }

  /**
   * Takes the surplus from the latest receipts first. Money held by a cheque clearing cannot move
   * to another instalment, nor be parked as an unmatched advance (a cheque payment is only ever
   * matched through its instrument), so such matches are left alone unless the surplus is being
   * refunded: what remains of a sliced match keeps its cheque, and the cheque's own flow cancels an
   * unpaid refund if the cheque later fails.
   */
  private List<Slice> slices(
      CollectionObligation row,
      BigDecimal amount,
      OverSettlementDecision decision,
      LocalDate effective) {
    boolean skipCheques = decision != OverSettlementDecision.REFUND;
    var slices = new ArrayList<Slice>();
    BigDecimal remaining = amount;
    for (var match : matches.holdingNewestFirst(TenantContext.requireTenantId(), row.getId())) {
      if (remaining.signum() == 0) break;
      if (skipCheques && match.getChequeId() != null) continue;
      if (effective.isBefore(match.getEffectiveDate()))
        throw CollectionRuleException.dateBeforeMatch(match.getEffectiveDate());
      BigDecimal take = match.getAmount().min(remaining);
      slices.add(new Slice(match, take));
      remaining = remaining.subtract(take);
    }
    if (remaining.signum() > 0)
      throw CollectionRuleException.conflict(
          "OVER_SETTLEMENT_NOT_MOVABLE",
          skipCheques
              ? "The surplus is held by cheque clearings; the money of a cleared cheque can only"
                  + " be refunded"
              : "The recorded matches no longer hold that amount");
    return slices;
  }

  private CollectionPaymentMatch again(
      CollectionObligation to,
      CollectionPaymentMatch from,
      UUID cheque,
      BigDecimal amount,
      CollectionRequests.ResolveOverSettlement request,
      UUID actor,
      String prefix) {
    Instant occurred = request.occurredAt();
    return matches.saveAndFlush(
        CollectionPaymentMatch.create(
            to.getId(),
            from.getPaymentId(),
            cheque,
            amount,
            request.effectiveDate(),
            occurred,
            actor,
            prefix + request.reason()));
  }

  private void changed(CollectionObligation row) {
    row.financialChange();
    obligations.saveAndFlush(row);
    events.changed(row.getId());
  }
}
