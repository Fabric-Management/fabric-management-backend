package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentMethod;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CollectionPostingService {
  private final CollectionCommandStore commands;
  private final CollectionObligationRepository obligations;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionProjectionRepository projections;
  private final CollectionProjectionService projectionService;
  private final CollectionAccessService access;
  private final PaymentService paymentService;
  private final PaymentRepository payments;
  private final FinancialPeriodGuard periods;
  private final CollectionEvents events;
  private final CollectionMatchReversal matchReversal;
  private final CollectionRefundLedger refundLedger;
  private final CollectionChequeApplicationRepository chequeApplications;

  public CollectionCommandReceipt match(
      UUID obligationId, CollectionRequests.Match request, UUID actor) {
    commands.validate(request);
    commands.lock();
    var row = writable(obligationId, actor);
    String hash = commands.hash("MATCH_PAYMENT", obligationId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    fresh(row);
    commands.version(request.expectedVersion(), row.getVersion());
    commands.time(request.effectiveDate(), request.occurredAt());
    var match =
        post(
            row,
            request.paymentId(),
            null,
            request.amount(),
            request.effectiveDate(),
            request.occurredAt(),
            actor,
            request.reason());
    return receipt(
        request.operationId(), actor, hash, row, request.paymentId(), List.of(match.getId()));
  }

  public CollectionCommandReceipt receive(
      UUID obligationId, CollectionRequests.Receipt request, UUID actor) {
    commands.validate(request);
    commands.lock();
    var row = writable(obligationId, actor);
    String hash = commands.hash("RECORD_RECEIPT", obligationId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    fresh(row);
    commands.version(request.expectedVersion(), row.getVersion());
    commands.time(request.effectiveDate(), request.occurredAt());
    if (request.method() == CollectionTypes.Method.CHEQUE)
      throw CollectionRuleException.invalid(
          "Receive a cheque as an instrument; only clearing records its payment");
    BigDecimal amount = CollectionAmounts.positive(request.amount(), row.getCurrency());
    row.requireMatchable(settled(row.getId()), amount);
    requireRoomBesideCheques(row, amount);
    periods.assertPostingAllowed(TenantContext.requireTenantId(), request.effectiveDate());
    var payment =
        paymentService.createPayment(
            new CreatePaymentRequest(
                row.getPartnerId(),
                "INBOUND",
                request.method().name(),
                amount,
                row.getCurrency(),
                request.effectiveDate(),
                request.bankReference(),
                request.reason(),
                null));
    var match =
        post(
            row,
            payment.id(),
            null,
            amount,
            request.effectiveDate(),
            request.occurredAt(),
            actor,
            request.reason());
    return receipt(request.operationId(), actor, hash, row, payment.id(), List.of(match.getId()));
  }

  public CollectionCommandReceipt reverse(
      UUID matchId, CollectionRequests.Reverse request, UUID actor) {
    commands.validate(request);
    commands.lock();
    var match =
        matches
            .findByTenantIdAndId(TenantContext.requireTenantId(), matchId)
            .orElseThrow(() -> new NotFoundException("Collection match not found"));
    var row = reversible(match.getObligationId(), actor);
    String hash = commands.hash("REVERSE_MATCH", matchId, request);
    var replay = commands.replay(request.operationId(), actor, hash);
    if (replay.isPresent()) return replay.get();
    commands.version(request.expectedVersion(), row.getVersion());
    commands.time(request.effectiveDate(), request.occurredAt());
    if (match.getChequeId() != null)
      throw CollectionRuleException.conflict(
          "CHEQUE_MATCH_REQUIRES_TRANSITION",
          "Reverse the cheque clearing as a whole to retain its instrument evidence");
    reverseMatch(match, request.effectiveDate(), request.occurredAt(), actor, request.reason());
    return receipt(request.operationId(), actor, hash, row, match.getPaymentId(), List.of(matchId));
  }

  CollectionCommandReceipt receipt(
      UUID operation,
      UUID actor,
      String hash,
      CollectionObligation row,
      UUID payment,
      List<UUID> ids) {
    obligations.flush();
    return commands.record(
        operation, actor, hash, "OBLIGATION", row.getId(), row.getVersion(), payment, ids);
  }

  CollectionObligation writable(UUID id, UUID actor) {
    var row =
        obligations
            .findByTenantIdAndId(TenantContext.requireTenantId(), id)
            .orElseThrow(() -> new NotFoundException("Collection obligation not found"));
    access.require(actor, "write", row);
    return row;
  }

  /** Undoing recorded money takes the reverse grant over this row, not merely the write grant. */
  CollectionObligation reversible(UUID id, UUID actor) {
    var row =
        obligations
            .findByTenantIdAndId(TenantContext.requireTenantId(), id)
            .orElseThrow(() -> new NotFoundException("Collection obligation not found"));
    requireReverse(row, actor);
    return row;
  }

  void requireReverse(CollectionObligation row, UUID actor) {
    access.requireGrant(actor, "reverse");
    access.require(actor, "reverse", row);
  }

  java.util.List<CollectionObligation> writableBatch(java.util.List<UUID> ids, UUID actor) {
    var tenant = TenantContext.requireTenantId();
    var reach = access.reach(actor, "write");
    var rows =
        obligations.findByTenantIdAndIdIn(tenant, ids).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    CollectionObligation::getId, java.util.function.Function.identity()));
    return ids.stream()
        .map(
            id -> {
              var row = rows.get(id);
              if (row == null
                  || !Boolean.TRUE.equals(row.getIsActive())
                  || !reach.permits(tenant, row.getCreatedBy(), row.getResponsibleUserId()))
                throw new NotFoundException("Collection obligation not found");
              return row;
            })
        .toList();
  }

  void fresh(CollectionObligation row) {
    freshAll(java.util.List.of(row));
  }

  /**
   * Brings the projection up to the agreement as it is now without requiring the agreement to
   * exist: for decisions about cash that is already recorded, which must stay possible after the
   * plan is cancelled or gone, but never against a plan that moved since it was read.
   */
  void refresh(CollectionObligation row) {
    projectionService.synchronize(row.getOrderId());
  }

  void freshAll(java.util.List<CollectionObligation> rows) {
    var orderIds =
        rows.stream()
            .map(CollectionObligation::getOrderId)
            .distinct()
            .sorted(java.util.Comparator.comparing(UUID::toString))
            .toList();
    orderIds.forEach(projectionService::synchronize);
    var sources = projections.findByTenantIdAndOrderIdIn(TenantContext.requireTenantId(), orderIds);
    if (sources.size() != orderIds.size()
        || sources.stream().anyMatch(value -> value.isSourceMissing()))
      throw CollectionRuleException.conflict(
          "COLLECTION_SOURCE_MISSING", "The source agreement could not be verified");
  }

  /**
   * Cash and cheques cover the same expectation. A cheque received but not yet cleared already
   * covers its part: cash matched over it would turn the clearing into a surplus nobody intended.
   */
  void requireRoomBesideCheques(CollectionObligation row, BigDecimal requested) {
    BigDecimal pending =
        chequeApplications.pending(TenantContext.requireTenantId(), List.of(row.getId())).stream()
            .findFirst()
            .map(CollectionPaymentMatchRepository.Balance::getAmount)
            .orElse(BigDecimal.ZERO);
    if (pending.signum() == 0) return;
    BigDecimal open =
        CollectionAmounts.outstanding(row.getAmount(), settled(row.getId())).subtract(pending);
    if (requested.compareTo(open) > 0)
      throw CollectionRuleException.conflict(
          "COLLECTION_PENDING_CHEQUE",
          "Cheques that have not cleared yet already cover "
              + pending.toPlainString()
              + " of this expectation; match at most "
              + open.max(BigDecimal.ZERO).toPlainString()
              + " in cash or cancel the cheque first");
  }

  BigDecimal settled(UUID id) {
    return matches.settled(TenantContext.requireTenantId(), List.of(id)).stream()
        .findFirst()
        .map(CollectionPaymentMatchRepository.Balance::getAmount)
        .orElse(BigDecimal.ZERO);
  }

  Payment payment(UUID id) {
    return payments
        .findByTenantIdAndId(TenantContext.requireTenantId(), id)
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .orElseThrow(() -> new NotFoundException("Payment not found"));
  }

  /** What a payment has already given away: matched to obligations plus set aside to refund. */
  BigDecimal claimed(UUID tenant, UUID paymentId) {
    return matches.matchedPayment(tenant, paymentId).add(refundLedger.held(tenant, paymentId));
  }

  boolean holdsRefund(UUID tenant, UUID paymentId) {
    return refundLedger.held(tenant, paymentId).signum() > 0;
  }

  void requirePayment(Payment payment, CollectionObligation row) {
    // A reversed payment and a reversal row are cancelled money, whatever status they keep.
    if (payment.getStatus() == PaymentStatus.VOIDED
        || payment.isReversed()
        || payment.isReversal()
        || payment.getDirection() != PaymentDirection.INBOUND
        || !payment.getTradingPartnerId().equals(row.getPartnerId())
        || !payment.getCurrency().equals(row.getCurrency()))
      throw CollectionRuleException.conflict(
          "COLLECTION_PAYMENT_INCOMPATIBLE",
          "Choose an active inbound payment for the same partner and currency");
  }

  /**
   * Collection and invoice bindings are independent axes of the same Payment, never added together.
   */
  CollectionPaymentMatch post(
      CollectionObligation row,
      UUID paymentId,
      UUID clearingChequeId,
      BigDecimal amount,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason) {
    BigDecimal exact = CollectionAmounts.positive(amount, row.getCurrency());
    var payment = payment(paymentId);
    requirePayment(payment, row);
    if (payment.getMethod() == PaymentMethod.CHEQUE && clearingChequeId == null)
      throw CollectionRuleException.conflict(
          "LEGACY_CHEQUE_UNVERIFIED",
          "A cheque payment requires an explicit instrument clearing, not a legacy payment method");
    if (effective.isBefore(payment.getPaymentDate()))
      throw CollectionRuleException.invalid("A match cannot precede the payment date");
    periods.assertPostingAllowed(TenantContext.requireTenantId(), effective);
    row.requireMatchable(settled(row.getId()), exact);
    // A clearing is the cheque's own coverage turning into money; only cash competes with it.
    if (clearingChequeId == null) requireRoomBesideCheques(row, exact);
    if (claimed(TenantContext.requireTenantId(), paymentId)
            .add(exact)
            .compareTo(payment.getAmount().getAmount())
        > 0)
      throw CollectionRuleException.conflict(
          "COLLECTION_PAYMENT_OVERMATCH",
          "Match exceeds the payment amount available for collection matching");
    var match =
        matches.saveAndFlush(
            CollectionPaymentMatch.create(
                row.getId(),
                paymentId,
                clearingChequeId,
                exact,
                effective,
                occurred,
                actor,
                reason));
    row.financialChange();
    obligations.saveAndFlush(row);
    events.changed(row.getId());
    return match;
  }

  java.util.List<UUID> postChequeBatch(
      UUID paymentId,
      UUID chequeId,
      java.util.List<CollectionObligation> rows,
      java.util.List<BigDecimal> amounts,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason) {
    var tenant = TenantContext.requireTenantId();
    var payment = payment(paymentId);
    if (effective.isBefore(payment.getPaymentDate()))
      throw CollectionRuleException.invalid("A match cannot precede the payment date");
    periods.assertPostingAllowed(tenant, effective);
    var settled =
        matches.settled(tenant, rows.stream().map(CollectionObligation::getId).toList()).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    CollectionPaymentMatchRepository.Balance::getId,
                    CollectionPaymentMatchRepository.Balance::getAmount));
    var postings = new java.util.ArrayList<CollectionPaymentMatch>();
    BigDecimal total = BigDecimal.ZERO;
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      requirePayment(payment, row);
      var amount = CollectionAmounts.positive(amounts.get(i), row.getCurrency());
      row.requireMatchable(settled.getOrDefault(row.getId(), BigDecimal.ZERO), amount);
      total = total.add(amount);
      postings.add(
          CollectionPaymentMatch.create(
              row.getId(), paymentId, chequeId, amount, effective, occurred, actor, reason));
    }
    if (claimed(tenant, paymentId).add(total).compareTo(payment.getAmount().getAmount()) > 0)
      throw CollectionRuleException.conflict(
          "COLLECTION_PAYMENT_OVERMATCH",
          "Match exceeds the payment amount available for collection matching");
    var saved = matches.saveAllAndFlush(postings);
    rows.forEach(CollectionObligation::financialChange);
    obligations.saveAllAndFlush(rows);
    rows.forEach(row -> events.changed(row.getId()));
    return saved.stream().map(CollectionPaymentMatch::getId).toList();
  }

  void reverseMatch(
      CollectionPaymentMatch match,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason) {
    matchReversal.reverse(match, effective, occurred, actor, reason);
  }
}
