package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.api.LotCompatibilityRequestPort;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceBasis;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceStatus;
import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.RemainingNeed;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeRequests;
import com.fabricmanagement.sales.orderintake.dto.QuantityAcceptanceDto;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityAcceptanceRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityProposalRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records which whole-piece option a line takes and, when it differs from the request, the
 * customer's acceptance (SOI D3, K08, A01, A11, IK-13). The accepted quantity and the line amount
 * change together; nothing is reserved until confirmation (A05).
 */
@Service
@RequiredArgsConstructor
public class QuantityAcceptanceService {

  static final String COMPATIBILITY_SOURCE = "SALES_ORDER_LINE";

  private final OrderIntakeAccess access;
  private final QuantityProposalRepository proposals;
  private final QuantityAcceptanceRepository acceptances;
  private final QuantityEvaluationService evaluation;
  private final SalesOrderLineRepository lines;
  private final LineAdjustmentGuard adjustments;
  private final com.fabricmanagement.sales.salesorder.app.SalesOrderRevision revision;
  private final LotCompatibilityRequestPort compatibilityRequests;
  private final Clock clock;

  @Transactional
  public QuantityAcceptanceDto record(
      UUID orderId, UUID lineId, OrderIntakeRequests.RecordQuantityAcceptance request, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    Optional<QuantityAcceptance> repeated = repeated(tenantId, request.idempotencyKey());
    if (repeated.isPresent()) {
      if (!repeated.get().getSalesOrderLineId().equals(lineId)) {
        throw OrderIntakeException.conflict(
            "IDEMPOTENCY_KEY_REUSED", "The idempotency key belongs to another line");
      }
      SalesOrder order = access.readableOrder(orderId, actor);
      return toDto(tenantId, repeated.get(), access.line(order, lineId));
    }

    SalesOrder order = access.writableOrder(orderId, actor);
    // The order row first (CEDIT-02 §5.5); decisions below are made on what is current after it.
    revision.lockFresh(order);
    access.requireActive(order);
    // Another recording of the same key may have committed while this one waited for the lock.
    Optional<QuantityAcceptance> recordedMeanwhile = repeated(tenantId, request.idempotencyKey());
    if (recordedMeanwhile.isPresent()) {
      if (!recordedMeanwhile.get().getSalesOrderLineId().equals(lineId)) {
        throw OrderIntakeException.conflict(
            "IDEMPOTENCY_KEY_REUSED", "The idempotency key belongs to another line");
      }
      return toDto(tenantId, recordedMeanwhile.get(), access.line(order, lineId));
    }
    if (order.getStatus() != OrderStatus.DRAFT) {
      throw OrderIntakeException.conflict(
          "ORDER_NOT_DRAFT", "Stock choices are recorded while the order is a draft");
    }
    order.assertCommercialContentEditable();
    SalesOrderLine line = access.line(order, lineId);
    revision.lockFresh(line);
    QuantityProposal proposal =
        proposals
            .findByTenantIdAndIdAndSalesOrderLineId(tenantId, request.proposalId(), lineId)
            .orElseThrow(
                () -> OrderIntakeException.notFound("Quantity proposal", request.proposalId()));
    requireLatestAndCurrent(tenantId, proposal, line);
    QuantityOption option =
        proposal
            .option(request.optionKey())
            .orElseThrow(
                () ->
                    OrderIntakeException.rule(
                        "OPTION_NOT_FOUND", "The option is not part of this proposal"));

    QuantityEvaluationService.LineStock stock =
        evaluation
            .currentStock(order, line)
            .orElseThrow(
                () ->
                    OrderIntakeException.conflict(
                        "EVIDENCE_UNAVAILABLE", "Current stock of the line cannot be read"));
    AcceptedStockCheck.Result check =
        AcceptedStockCheck.check(stock, option.pieceIds(), option.batchIds());
    if (!check.piecesAvailable()
        || check.canonicalQuantity().compareTo(option.canonicalQuantity()) != 0) {
      throw OrderIntakeException.conflict(
          "PROPOSAL_STALE",
          "Pieces of this option are no longer available: " + check.unavailablePieces());
    }
    if (line.isSingleLotRequired() && option.lotCount() > 1) {
      throw OrderIntakeException.rule(
          "SINGLE_LOT_REQUIRED", "The customer requires the distribution from a single lot");
    }

    // EXACT and REQUESTED_WITH_REMNANT deliver the requested quantity: no customer acceptance is
    // involved, only the salesperson's remnant decision (A01). Other kinds change the quantity.
    QuantityAcceptanceBasis basis =
        option.kind() == QuantityOption.OptionKind.EXACT
                || option.kind() == QuantityOption.OptionKind.REQUESTED_WITH_REMNANT
            ? QuantityAcceptanceBasis.EXACT_MATCH
            : QuantityAcceptanceBasis.CUSTOMER_ACCEPTED;
    QuantityAcceptance.CustomerEvidence evidence = evidence(request, basis);
    if (option.needsRemnantAcknowledgement() && !request.remnantAcknowledged()) {
      throw OrderIntakeException.rule(
          "REMNANT_NOT_ACKNOWLEDGED",
          "This option leaves, or may leave, a single piece in a lot; acknowledge the warning to continue");
    }
    RemainingNeed remainingNeed = null;
    BigDecimal remainingQty = null;
    BigDecimal lineQty = option.quantity();
    if (option.kind() == QuantityOption.OptionKind.BELOW) {
      if (request.remainingNeed() == null) {
        throw OrderIntakeException.rule(
            "REMAINING_NEED_REQUIRED",
            "Say whether the customer reduced the order or the rest stays open");
      }
      remainingNeed = request.remainingNeed();
      remainingQty = line.getRequestedQty().subtract(option.quantity());
      if (remainingNeed == RemainingNeed.REMAINS_OPEN) {
        lineQty = line.getRequestedQty();
      }
    }

    Instant now = clock.instant();
    acceptances
        .findFirstByTenantIdAndSalesOrderLineIdAndStatus(
            tenantId, lineId, QuantityAcceptanceStatus.ACTIVE)
        .ifPresent(
            previous -> {
              previous.supersede(now);
              acceptances.save(previous);
            });
    compatibilityRequests.withdrawOpen(tenantId, lineId, actor);

    QuantityAcceptance acceptance =
        QuantityAcceptance.record(
            order.getId(),
            lineId,
            proposal.getId(),
            option,
            check.compatibility(),
            option.quantity(),
            line.getUnit(),
            basis,
            request.remnantAcknowledged(),
            remainingNeed,
            remainingQty,
            evidence,
            actor,
            now,
            request.idempotencyKey());

    line.setRequestedQty(lineQty);
    adjustments.assertFits(line);
    lines.save(line);
    revision.linesChanged(order);
    acceptance.coverTerms(AcceptanceTerms.fingerprint(line));
    QuantityAcceptance saved = acceptances.save(acceptance);

    if (saved.isConditional()) {
      compatibilityRequests.request(
          tenantId,
          new HashSet<>(saved.getBatchIds()),
          line.getProductId(),
          order.getTradingPartnerId(),
          COMPATIBILITY_SOURCE,
          lineId,
          "Customer answered a multi-lot option; tone compatibility is not confirmed",
          actor);
    }
    return toDto(tenantId, saved, line);
  }

  @Transactional
  public void withdraw(UUID orderId, UUID lineId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = access.writableOrder(orderId, actor);
    revision.lockFresh(order);
    access.requireActive(order);
    if (order.getStatus() != OrderStatus.DRAFT) {
      throw OrderIntakeException.conflict(
          "ORDER_NOT_DRAFT", "Stock choices change only while the order is a draft");
    }
    order.assertCommercialContentEditable();
    SalesOrderLine line = access.line(order, lineId);
    revision.lockFresh(line);
    QuantityAcceptance active =
        acceptances
            .findFirstByTenantIdAndSalesOrderLineIdAndStatus(
                tenantId, lineId, QuantityAcceptanceStatus.ACTIVE)
            .orElseThrow(() -> OrderIntakeException.notFound("Active acceptance of line", lineId));
    active.withdraw(clock.instant());
    acceptances.save(active);
    compatibilityRequests.withdrawOpen(tenantId, lineId, actor);
    if (line.getInitialRequestedQty() != null
        && line.getRequestedQty().compareTo(line.getInitialRequestedQty()) != 0) {
      // The acceptance had moved the line to the accepted quantity; the customer's request stands
      // again.
      line.setRequestedQty(line.getInitialRequestedQty());
      adjustments.assertFits(line);
      lines.save(line);
      revision.linesChanged(order);
    }
  }

  @Transactional(readOnly = true)
  public List<QuantityAcceptanceDto> history(UUID orderId, UUID lineId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = access.readableOrder(orderId, actor);
    SalesOrderLine line = access.line(order, lineId);
    return acceptances
        .findByTenantIdAndSalesOrderLineIdOrderByRecordedAtDescIdDesc(tenantId, lineId)
        .stream()
        .map(value -> toDto(tenantId, value, line))
        .toList();
  }

  private void requireLatestAndCurrent(
      UUID tenantId, QuantityProposal proposal, SalesOrderLine line) {
    UUID latest =
        proposals
            .findFirstByTenantIdAndSalesOrderLineIdOrderByEvaluatedAtDescIdDesc(
                tenantId, line.getId())
            .map(QuantityProposal::getId)
            .orElse(null);
    if (!proposal.getId().equals(latest)) {
      throw OrderIntakeException.conflict(
          "PROPOSAL_STALE", "A newer proposal exists for this line; decide on the latest one");
    }
    // A product correction leaves the earlier proposal behind: it offered another product's
    // pieces, whatever the stock of the corrected product holds now (CEDIT-03).
    if (!proposal.getProductId().equals(line.getProductId())) {
      throw OrderIntakeException.conflict(
          "PROPOSAL_STALE", "The line's product changed after this proposal; evaluate again");
    }
    boolean sameRequest =
        proposal.getRequestedQty().compareTo(line.getRequestedQty()) == 0
            && proposal
                .getUnit()
                .trim()
                .toUpperCase(Locale.ROOT)
                .equals(line.getUnit().trim().toUpperCase(Locale.ROOT));
    if (!sameRequest) {
      throw OrderIntakeException.conflict(
          "PROPOSAL_STALE", "The line changed after this proposal; evaluate again");
    }
  }

  private static QuantityAcceptance.CustomerEvidence evidence(
      OrderIntakeRequests.RecordQuantityAcceptance request, QuantityAcceptanceBasis basis) {
    boolean any =
        notBlank(request.customerContact())
            || request.channel() != null
            || request.acceptedAt() != null
            || notBlank(request.evidenceNote())
            || request.evidenceAttachmentId() != null;
    if (basis == QuantityAcceptanceBasis.CUSTOMER_ACCEPTED) {
      if (!notBlank(request.customerContact())
          || request.channel() == null
          || request.acceptedAt() == null) {
        throw OrderIntakeException.rule(
            "CUSTOMER_EVIDENCE_REQUIRED",
            "Record who accepted, through which channel and when (SOI A11)");
      }
      if (!request.customerStatementConfirmed()) {
        throw OrderIntakeException.rule(
            "CUSTOMER_STATEMENT_REQUIRED",
            "Confirm that you received this acceptance from the customer");
      }
    }
    if (!any) {
      return null;
    }
    return new QuantityAcceptance.CustomerEvidence(
        request.customerContact(),
        request.channel(),
        request.acceptedAt(),
        request.evidenceNote(),
        request.evidenceAttachmentId(),
        request.customerStatementConfirmed());
  }

  private Optional<QuantityAcceptance> repeated(UUID tenantId, String key) {
    return notBlank(key)
        ? acceptances.findByTenantIdAndIdempotencyKey(tenantId, key.trim())
        : Optional.empty();
  }

  private QuantityAcceptanceDto toDto(
      UUID tenantId, QuantityAcceptance value, SalesOrderLine line) {
    String requestStatus = null;
    if (value.isConditional()) {
      String key = sortedKey(value.getBatchIds());
      requestStatus =
          compatibilityRequests.forSource(tenantId, line.getId()).stream()
              .filter(request -> sortedKey(request.batchIds()).equals(key))
              .map(LotCompatibilityRequestPort.RequestView::status)
              .findFirst()
              .orElse(null);
    }
    return QuantityAcceptanceDto.from(value, AcceptanceTerms.fingerprint(line), requestStatus);
  }

  private static String sortedKey(List<UUID> ids) {
    return ids.stream()
        .distinct()
        .sorted()
        .map(UUID::toString)
        .reduce((a, b) -> a + "," + b)
        .orElse("");
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
