package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.*;
import com.fabricmanagement.finance.collection.dto.CollectionDtos;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionQueryService {
  private final CollectionAccessService access;
  private final CollectionObligationRepository obligations;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionProjectionRepository projections;
  private final CollectionProjectionService projectionService;
  private final CollectionChequeRepository cheques;
  private final CollectionChequeApplicationRepository applications;
  private final CollectionChequeEventRepository chequeEvents;
  private final CollectionExpectationRepository expectations;
  private final PaymentRepository payments;
  private final CollectionRefundLedger refundLedger;

  @Transactional(readOnly = true)
  public Page<CollectionDtos.Obligation> list(
      UUID actor,
      UUID partner,
      UUID order,
      String currency,
      Boolean cancelled,
      LocalDate from,
      LocalDate to,
      Pageable pageable) {
    var reach = access.reach(actor, "read");
    var write = access.reach(actor, "write");
    var reverse = access.reach(actor, "reverse");
    Specification<CollectionObligation> filter =
        reach
            .restriction()
            .and(
                (root, q, cb) -> {
                  var tests = new ArrayList<jakarta.persistence.criteria.Predicate>();
                  if (partner != null) tests.add(cb.equal(root.get("partnerId"), partner));
                  if (order != null) tests.add(cb.equal(root.get("orderId"), order));
                  if (currency != null) tests.add(cb.equal(root.get("currency"), currency));
                  if (cancelled != null) tests.add(cb.equal(root.get("cancelled"), cancelled));
                  if (from != null)
                    tests.add(cb.greaterThanOrEqualTo(root.get("expectedDate"), from));
                  if (to != null) tests.add(cb.lessThanOrEqualTo(root.get("expectedDate"), to));
                  return cb.and(tests.toArray(jakarta.persistence.criteria.Predicate[]::new));
                });
    var page = obligations.findAll(filter, page(pageable, Sort.by(Sort.Direction.ASC, "id")));
    var rows = page.getContent();
    if (rows.isEmpty())
      return new org.springframework.data.domain.PageImpl<>(
          List.of(), page.getPageable(), page.getTotalElements());
    var tenant = TenantContext.requireTenantId();
    var ids = rows.stream().map(CollectionObligation::getId).toList();
    var settled = balances(matches.settled(tenant, ids));
    var cash = balances(matches.nonChequeSettledFor(tenant, ids));
    var received = balances(applications.received(tenant, ids));
    var owedBack = refundLedger.pendingByObligation(tenant, ids);
    var sources =
        projections
            .findByTenantIdAndOrderIdIn(
                tenant, rows.stream().map(CollectionObligation::getOrderId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(CollectionProjection::getOrderId, Function.identity()));
    return page.map(
        row ->
            view(
                row,
                settled.getOrDefault(row.getId(), BigDecimal.ZERO),
                received.getOrDefault(row.getId(), BigDecimal.ZERO),
                cash.getOrDefault(row.getId(), BigDecimal.ZERO),
                owedBack.getOrDefault(row.getId(), BigDecimal.ZERO),
                sources.get(row.getOrderId()),
                write,
                reverse,
                null));
  }

  @Transactional
  public CollectionDtos.Detail detail(UUID id, UUID actor, Pageable pageable) {
    var row = readable(id, actor);
    // A screen can be shown from the last recorded projection: it waits for no writer, and a
    // source that cannot be read costs the page nothing. The notice says the refresh was skipped,
    // never that it was current. When the tenant lock was free the reads below are made under it,
    // as one consistent view; otherwise each is consistent and the notice says the page may lag.
    var refresh = projectionService.refreshIfFree(row.getOrderId());
    UUID tenant = TenantContext.requireTenantId();
    var amounts = balances(matches.settled(tenant, List.of(id)));
    var received = balances(applications.received(tenant, List.of(id)));
    var view =
        view(
            row,
            amounts.getOrDefault(id, BigDecimal.ZERO),
            received.getOrDefault(id, BigDecimal.ZERO),
            matches.nonChequeSettled(tenant, id),
            refundLedger.pendingByObligation(tenant, List.of(id)).getOrDefault(id, BigDecimal.ZERO),
            projections.findByTenantIdAndOrderId(tenant, row.getOrderId()).orElse(null),
            access.reach(actor, "write"),
            access.reach(actor, "reverse"),
            noticeOf(refresh));
    var matchPage =
        matches.findByTenantIdAndObligationId(
            tenant, id, page(pageable, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    var ids =
        matchPage.getContent().stream()
            .map(CollectionPaymentMatch::getPaymentId)
            .distinct()
            .toList();
    var statuses =
        ids.isEmpty()
            ? Map.<UUID, String>of()
            : payments.findByTenantIdAndIdIn(tenant, ids).stream()
                .collect(
                    Collectors.toMap(value -> value.getId(), value -> value.getStatus().name()));
    return new CollectionDtos.Detail(
        view,
        matchPage.map(
            value ->
                new CollectionDtos.Match(
                    value.getId(),
                    value.getVersion(),
                    id,
                    value.getPaymentId(),
                    value.getChequeId(),
                    value.getAmount().toPlainString(),
                    value.getEffectiveDate(),
                    value.getOccurredAt(),
                    value.getCreatedAt(),
                    value.getActorId(),
                    value.getReason(),
                    value.getReversedOn(),
                    value.getReversalOccurredAt(),
                    value.getReversedAt(),
                    value.getReversedBy(),
                    value.getReversalReason(),
                    value.getPaymentInvalidatedAt(),
                    value.getInvalidationRecordedAt(),
                    statuses.get(value.getPaymentId()))));
  }

  /** Every refund this expectation's surplus ever opened, in the order they were opened. */
  @Transactional(readOnly = true)
  public List<CollectionDtos.Refund> refunds(UUID id, UUID actor) {
    var row = readable(id, actor);
    boolean write =
        access
            .reach(actor, "write")
            .permits(row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId());
    boolean reverse =
        access
            .reach(actor, "reverse")
            .permits(row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId());
    return refundLedger.forObligation(TenantContext.requireTenantId(), id).stream()
        .map(view -> refund(view, write, reverse))
        .toList();
  }

  /** Paying a refund takes write; stopping to owe it undoes recorded money, so it takes reverse. */
  private static CollectionDtos.Refund refund(
      CollectionRefundLedger.View view, boolean write, boolean reverse) {
    var refund = view.refund();
    boolean open = view.state() == RefundState.OPEN;
    var caps = new ArrayList<CollectionDtos.Capability>();
    for (var action : List.of("SETTLE_REFUND", "CANCEL_REFUND")) {
      boolean allowed = action.equals("SETTLE_REFUND") ? write : reverse;
      caps.add(
          new CollectionDtos.Capability(
              action,
              allowed && open,
              !allowed ? "PERMISSION_DENIED" : !open ? "WRONG_STATE" : null,
              null,
              false));
    }
    return new CollectionDtos.Refund(
        refund.getId(),
        refund.getObligationId(),
        refund.getPaymentId(),
        refund.getPartnerId(),
        refund.getCurrency(),
        refund.getAmount().toPlainString(),
        view.state(),
        view.step(),
        view.settlementPaymentId(),
        refund.getEffectiveDate(),
        refund.getOccurredAt(),
        refund.getCreatedAt(),
        refund.getActorId(),
        refund.getReason(),
        refund.getReleasedAllocations().stream()
            .map(
                value ->
                    new CollectionDtos.ReleasedAllocation(
                        value.allocationId(), value.invoiceId(), value.amount()))
            .toList(),
        caps);
  }

  @Transactional(readOnly = true)
  public Page<CollectionDtos.Expectation> history(UUID id, UUID actor, Pageable pageable) {
    readable(id, actor);
    return expectations
        .findByTenantIdAndObligationId(
            TenantContext.requireTenantId(),
            id,
            page(pageable, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
        .map(
            value ->
                new CollectionDtos.Expectation(
                    value.getRevisionId(),
                    value.getApprovalId(),
                    value.getAgreedAt(),
                    value.getActivatedAt(),
                    value.getCreatedAt(),
                    value.getAmount().toPlainString(),
                    value.getKind(),
                    value.getMethod(),
                    value.getExpectedDate(),
                    value.isAwaitsConfirmationDate(),
                    value.getChequePaymentDate()));
  }

  @Transactional(readOnly = true)
  public Page<UUID> chequeIds(UUID id, UUID actor, Pageable pageable) {
    readable(id, actor);
    return applications.chequeIds(
        TenantContext.requireTenantId(), id, page(pageable, Sort.unsorted()));
  }

  @Transactional(readOnly = true)
  public CollectionDtos.Cheque cheque(UUID id, UUID actor) {
    UUID tenant = TenantContext.requireTenantId();
    var cheque = readableCheque(id, actor);
    var write = access.reach(actor, "write");
    var reverse = access.reach(actor, "reverse");
    boolean allowed = write.permits(tenant, cheque.getCreatedBy(), null);
    boolean mayReverse = reverse.permits(tenant, cheque.getCreatedBy(), null);
    var applied = applications.findByTenantIdAndChequeId(tenant, id);
    if (!applied.isEmpty()) {
      allowed = true;
      mayReverse = true;
    }
    var read = access.reach(actor, "read");
    var rows =
        obligations.findByTenantIdAndIdIn(
            tenant, applied.stream().map(CollectionChequeApplication::getObligationId).toList());
    if (rows.size() != applied.size()) throw new NotFoundException("Cheque application not found");
    for (var row : rows) {
      if (!read.permits(tenant, row.getCreatedBy(), row.getResponsibleUserId()))
        throw new NotFoundException("Cheque application not found");
      allowed &= write.permits(tenant, row.getCreatedBy(), row.getResponsibleUserId());
      mayReverse &= reverse.permits(tenant, row.getCreatedBy(), row.getResponsibleUserId());
    }
    boolean valid =
        cheque.getClearedPaymentId() != null
            && payments
                .findByTenantIdAndId(tenant, cheque.getClearedPaymentId())
                .filter(
                    payment ->
                        Boolean.TRUE.equals(payment.getIsActive())
                            && payment.getStatus() != PaymentStatus.VOIDED)
                .isPresent();
    var warnings = new ArrayList<String>();
    if (cheque.getState() == ChequeState.CLEARED && !valid)
      warnings.add("CLEARING_PAYMENT_INVALIDATED");
    boolean duplicate =
        cheques.existsByTenantIdAndPartnerIdAndCurrencyAndInstrumentReferenceAndIdNot(
            tenant,
            cheque.getPartnerId(),
            cheque.getCurrency(),
            cheque.getInstrumentReference(),
            id);
    if (duplicate) warnings.add("POSSIBLE_DUPLICATE_CHEQUE_REFERENCE");
    var caps = new ArrayList<CollectionDtos.Capability>();
    for (var action : ChequeAction.values()) {
      boolean legal = cheque.getState().permits(action);
      // Dishonouring or returning a cleared instrument undoes money: it also needs reverse.
      boolean granted = allowed && (!cheque.getState().reversesClearing(action) || mayReverse);
      caps.add(
          new CollectionDtos.Capability(
              action.name(),
              granted && legal,
              !granted ? "PERMISSION_DENIED" : !legal ? "WRONG_STATE" : null,
              null,
              false));
    }
    return new CollectionDtos.Cheque(
        id,
        cheque.getVersion(),
        cheque.getPartnerId(),
        cheque.getCurrency(),
        cheque.getAmount().toPlainString(),
        cheque
            .getAmount()
            .subtract(
                applied.stream()
                    .map(CollectionChequeApplication::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
            .max(BigDecimal.ZERO)
            .toPlainString(),
        cheque.getReceivedOn(),
        cheque.getDueOn(),
        cheque.getInstrumentReference(),
        cheque.getState(),
        cheque.getClearedPaymentId(),
        valid,
        duplicate,
        applied.stream()
            .map(
                value ->
                    new CollectionDtos.Application(
                        value.getObligationId(), value.getAmount().toPlainString()))
            .toList(),
        caps,
        warnings);
  }

  @Transactional(readOnly = true)
  public Page<CollectionDtos.ChequeEvent> chequeHistory(UUID id, UUID actor, Pageable pageable) {
    readableCheque(id, actor);
    return chequeEvents
        .findByTenantIdAndChequeId(
            TenantContext.requireTenantId(),
            id,
            page(pageable, Sort.by(Sort.Direction.DESC, "evidenceRevision", "createdAt", "id")))
        .map(
            value ->
                new CollectionDtos.ChequeEvent(
                    value.getId(),
                    value.getAction(),
                    value.getState(),
                    value.getEffectiveDate(),
                    value.getOccurredAt(),
                    value.getCreatedAt(),
                    value.getActorId(),
                    value.getReason(),
                    value.getEvidence(),
                    value.getAmount().toPlainString(),
                    value.getReceivedOn(),
                    value.getDueOn(),
                    value.getInstrumentReference(),
                    value.getPaymentId()));
  }

  private CollectionObligation readable(UUID id, UUID actor) {
    var row =
        obligations
            .findByTenantIdAndId(TenantContext.requireTenantId(), id)
            .orElseThrow(() -> new NotFoundException("Collection obligation not found"));
    access.require(actor, "read", row);
    return row;
  }

  private CollectionCheque readableCheque(UUID id, UUID actor) {
    var cheque =
        cheques
            .findByTenantIdAndId(TenantContext.requireTenantId(), id)
            .orElseThrow(() -> new NotFoundException("Cheque not found"));
    var reach = access.reach(actor, "read");
    var applied = applications.findByTenantIdAndChequeId(cheque.getTenantId(), id);
    var linked =
        obligations.findByTenantIdAndIdIn(
            cheque.getTenantId(),
            applied.stream().map(CollectionChequeApplication::getObligationId).toList());
    // An appointed finance colleague may work the whole instrument only when every application
    // is in their current scope. Unapplied instruments retain creator/global scope.
    if (linked.size() != applied.size()
        || (applied.isEmpty() && !reach.permits(cheque.getTenantId(), cheque.getCreatedBy(), null))
        || linked.stream()
            .anyMatch(
                row ->
                    !reach.permits(
                        row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId())))
      throw new NotFoundException("Cheque not found");
    return cheque;
  }

  private static String noticeOf(CollectionProjectionService.Refresh refresh) {
    return switch (refresh) {
      case REFRESHED -> null;
      case BUSY -> "SOURCE_REFRESH_DEFERRED";
      case UNREADABLE -> "SOURCE_UNREADABLE";
    };
  }

  private static String money(BigDecimal amount, CollectionObligation row) {
    return CollectionAmounts.text(amount, row.getCurrency());
  }

  private CollectionDtos.Obligation view(
      CollectionObligation row,
      BigDecimal settled,
      BigDecimal received,
      BigDecimal cash,
      BigDecimal owedBack,
      CollectionProjection source,
      CollectionAccessService.Reach write,
      CollectionAccessService.Reach reverse,
      String refreshNotice) {
    boolean sourceMissing = source == null || source.isSourceMissing();
    boolean stale = source != null && source.isStale();
    var warnings = new ArrayList<String>();
    if (sourceMissing) warnings.add("SOURCE_MISSING");
    if (stale) warnings.add("SOURCE_STALE");
    if (refreshNotice != null) warnings.add(refreshNotice);
    if (row.isAwaitsConfirmationDate()) warnings.add("CONFIRMATION_DATE_UNRESOLVED");
    var over = CollectionAmounts.surplus(row.getAmount(), row.isCancelled(), settled);
    if (row.getMethod() == Method.CHEQUE && received.add(cash).compareTo(row.getAmount()) > 0)
      warnings.add("EXCESS_INSTRUMENTS_REQUIRE_RECONCILIATION");
    if (over.signum() > 0) warnings.add("RECEIPTS_REQUIRE_RECONCILIATION");
    if (owedBack.signum() > 0) warnings.add("REFUND_PENDING");
    if (row.isCancelled() && (settled.signum() > 0 || received.signum() > 0))
      warnings.add("CANCELLED_EXPECTATION_HAS_RECEIPTS_OR_INSTRUMENTS");
    boolean mayWrite =
        write.permits(row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId());
    boolean mayReverse =
        reverse.permits(row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId());
    boolean payable =
        !row.isCancelled() && !sourceMissing && settled.compareTo(row.getAmount()) < 0;
    var caps = new ArrayList<CollectionDtos.Capability>();
    for (var action : Action.values()) {
      boolean legal =
          switch (action) {
            case REVERSE_MATCH -> true;
            case RESOLVE_OVER_SETTLEMENT, REFUND_OVER_SETTLEMENT -> over.signum() > 0;
            case RECEIVE_CHEQUE ->
                payable
                    && row.getMethod() == Method.CHEQUE
                    && row.getAmount().subtract(received).subtract(cash).signum() > 0;
            case MATCH_PAYMENT, RECORD_RECEIPT -> payable;
          };
      boolean allowed =
          action == Action.REVERSE_MATCH || action == Action.REFUND_OVER_SETTLEMENT
              ? mayReverse
              : mayWrite;
      String unavailable =
          action == Action.RESOLVE_OVER_SETTLEMENT || action == Action.REFUND_OVER_SETTLEMENT
              ? "NO_OVER_SETTLEMENT"
              : "EXPECTATION_NOT_OPEN";
      caps.add(
          new CollectionDtos.Capability(
              action.name(),
              allowed && legal,
              !allowed ? "PERMISSION_DENIED" : !legal ? unavailable : null,
              null,
              false));
    }
    String status =
        sourceMissing
                || over.signum() > 0
                || row.getMethod() == Method.CHEQUE
                    && received.add(cash).compareTo(row.getAmount()) > 0
                || row.isCancelled() && (settled.signum() > 0 || received.signum() > 0)
            ? "REVIEW_REQUIRED"
            : row.isCancelled()
                ? "CANCELLED"
                : settled.compareTo(row.getAmount()) >= 0
                    ? "SETTLED"
                    : settled.signum() > 0 ? "PARTIALLY_SETTLED" : "OPEN";
    return new CollectionDtos.Obligation(
        row.getId(),
        row.getVersion(),
        row.getLogicalId(),
        row.getOrderId(),
        row.getPartnerId(),
        row.getPlanId(),
        row.getRevisionId(),
        row.getCurrency(),
        money(row.getAmount(), row),
        money(settled, row),
        money(
            row.isCancelled()
                ? BigDecimal.ZERO
                : CollectionAmounts.outstanding(row.getAmount(), settled),
            row),
        money(received, row),
        money(
            row.isCancelled() || row.getMethod() != Method.CHEQUE
                ? BigDecimal.ZERO
                : row.getAmount().subtract(received).subtract(cash).max(BigDecimal.ZERO),
            row),
        money(over, row),
        money(owedBack, row),
        row.getKind(),
        row.getMethod(),
        row.getExpectedDate(),
        row.getChequePaymentDate(),
        row.isAwaitsConfirmationDate(),
        row.isCancelled(),
        status,
        source == null ? null : source.getCheckedAt(),
        sourceMissing,
        stale,
        warnings,
        caps);
  }

  private static Map<UUID, BigDecimal> balances(
      List<CollectionPaymentMatchRepository.Balance> values) {
    return values.stream()
        .collect(
            Collectors.toMap(
                CollectionPaymentMatchRepository.Balance::getId,
                CollectionPaymentMatchRepository.Balance::getAmount));
  }

  private static Pageable page(Pageable input, Sort sort) {
    return PageRequest.of(input.getPageNumber(), Math.min(input.getPageSize(), 100), sort);
  }

  public static CollectionDtos.Result result(CollectionCommandReceipt value) {
    return new CollectionDtos.Result(
        value.getOperationId(),
        value.getResourceType(),
        value.getResourceId(),
        value.getResourceVersion(),
        value.getPaymentId(),
        value.getMatchIds());
  }
}
