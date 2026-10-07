package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.CustomerToneAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.dto.QuantityProposalDto;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerToneAcceptanceRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityProposalRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates a line's requested quantity against whole pieces in stock and records the proposal (SOI
 * D2). Reads stock; reserves nothing; never changes the line.
 */
@Service
@RequiredArgsConstructor
public class QuantityEvaluationService {

  private static final String UNKNOWN_CANONICAL_UNIT = "?";

  private static final ProposalStockQueryService.ProposalStock NO_STOCK =
      new ProposalStockQueryService.ProposalStock(List.of(), List.of());

  private final OrderIntakeAccess access;
  private final ProductSalesDefinitionQueryService productDefinitions;
  private final ProposalStockQueryService stockQuery;
  private final CustomerToneAcceptanceRepository toneAcceptances;
  private final QuantityProposalRepository proposals;
  private final Clock clock;

  @Transactional
  public QuantityProposalDto evaluate(UUID orderId, UUID lineId, UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    if (order.getStatus() != OrderStatus.DRAFT) {
      throw OrderIntakeException.conflict(
          "ORDER_NOT_DRAFT", "Quantities are evaluated while the order is a draft");
    }
    SalesOrderLine line = access.line(order, lineId);
    QuantityProposal proposal = evaluateAndRecord(order, line, actor);
    return QuantityProposalDto.from(proposal);
  }

  @Transactional(readOnly = true)
  public Optional<QuantityProposalDto> latest(UUID orderId, UUID lineId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    SalesOrderLine line = access.line(order, lineId);
    return proposals
        .findFirstByTenantIdAndSalesOrderLineIdOrderByEvaluatedAtDescIdDesc(
            TenantContext.requireTenantId(), line.getId())
        .map(QuantityProposalDto::from);
  }

  /**
   * Re-evaluates without persisting. Used by acceptance and confirmation to detect that stock
   * changed since the proposal the customer accepted.
   */
  @Transactional(readOnly = true)
  public QuantityEvaluator.Evaluation reevaluate(SalesOrder order, SalesOrderLine line) {
    return evaluation(order, line);
  }

  QuantityProposal evaluateAndRecord(SalesOrder order, SalesOrderLine line, UUID actor) {
    QuantityEvaluator.Evaluation evaluation = evaluation(order, line);
    return proposals.save(
        QuantityProposal.record(
            order.getId(),
            line.getId(),
            line.getProductId(),
            line.getRequestedQty(),
            line.getUnit(),
            evaluation.result(),
            evaluation.evidenceFingerprint(),
            actor,
            Instant.now(clock)));
  }

  /**
   * The current piece-level stock of the line's distribution with the compatibility evidence that
   * applies to this customer. Empty when the product's measure or the line unit cannot be resolved.
   */
  @Transactional(readOnly = true)
  public Optional<LineStock> currentStock(SalesOrder order, SalesOrderLine line) {
    UUID tenantId = TenantContext.requireTenantId();
    Optional<ProductSalesDefinitionDto> product =
        productDefinitions.find(tenantId, line.getProductId());
    if (product.isEmpty()) {
      return Optional.empty();
    }
    Optional<PrimaryMeasure> measure = stockQuery.measureFor(product.get().productType());
    if (measure.isEmpty()) {
      return Optional.empty();
    }
    ProposalStockQueryService.ProposalStock stock =
        stockQuery.find(
            new ProposalStockQueryService.ProposalStockQuery(
                tenantId,
                line.getProductId(),
                line.getColorId(),
                line.getFinishedWidth(),
                line.getFinishedWidthUnit(),
                order.getTradingPartnerId()));
    return Optional.of(
        new LineStock(
            measure.get(),
            stock.lots(),
            stock.confirmedCompatibleGroups(),
            toneGroups(tenantId, order, line)));
  }

  /** Converts a canonical stock quantity into the line unit; empty when no exact conversion. */
  public Optional<BigDecimal> toLineUnit(
      BigDecimal canonical, SalesOrderLine line, PrimaryMeasure measure) {
    return stockQuery
        .fromCanonical(canonical, line.getUnit(), measure)
        .map(value -> value.setScale(3, RoundingMode.HALF_UP));
  }

  private List<Set<UUID>> toneGroups(UUID tenantId, SalesOrder order, SalesOrderLine line) {
    return toneAcceptances
        .findByTenantIdAndCustomerIdAndIsActiveTrue(tenantId, order.getTradingPartnerId())
        .stream()
        .filter(
            acceptance ->
                acceptance.isCustomerStatementConfirmed()
                    && line.getId().equals(acceptance.getSalesOrderLineId()))
        .map(CustomerToneAcceptance::getBatchIds)
        .map(Set::copyOf)
        .toList();
  }

  /** Piece-level stock of one distribution, as the proposal and the acceptance checks see it. */
  public record LineStock(
      PrimaryMeasure measure,
      List<ProposalStockQueryService.ProposalLot> lots,
      List<Set<UUID>> confirmedGroups,
      List<Set<UUID>> toneGroups) {}

  private QuantityEvaluator.Evaluation evaluation(SalesOrder order, SalesOrderLine line) {
    UUID tenantId = TenantContext.requireTenantId();
    return run(new EvaluationRequest(
            line.getId(),
            line.getProductId(),
            line.getColorId(),
            line.getFinishedWidth(),
            line.getFinishedWidthUnit(),
            order.getTradingPartnerId(),
            line.getRequestedQty(),
            line.getUnit(),
            line.isSingleLotRequired(),
            line.getToleranceUpPct(),
            line.getToleranceDownPct(),
            toneGroups(tenantId, order, line)))
        .evaluation();
  }

  /**
   * The one evaluation path (STOCK-PREVIEW-1 A1): a saved line and an unsaved preview are evaluated
   * by the same evaluator, from the same stock read and with the same unit conversion. Stock is
   * read once, as soon as the product's measure is known, and returned with the result so a caller
   * can describe exactly the lots the options were computed from. Persists nothing.
   *
   * <p>Public, not package-visible, because callers reach it through this bean's class-based
   * transaction proxy.
   */
  public EvaluationRun run(EvaluationRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    ProductSalesDefinitionDto product =
        productDefinitions
            .find(tenantId, request.productId())
            .orElseThrow(() -> OrderIntakeException.productNotAvailable(request.productId()));
    Optional<PrimaryMeasure> measure = stockQuery.measureFor(product.productType());
    if (measure.isEmpty()) {
      return new EvaluationRun(
          unknown(request, UNKNOWN_CANONICAL_UNIT, "MEASURE_UNKNOWN"),
          null,
          UNKNOWN_CANONICAL_UNIT,
          NO_STOCK);
    }
    PrimaryMeasure dimension = measure.get();
    String canonicalUnit = stockQuery.canonicalUnit(dimension);
    ProposalStockQueryService.ProposalStock stock =
        stockQuery.find(
            new ProposalStockQueryService.ProposalStockQuery(
                tenantId,
                request.productId(),
                request.colorId(),
                request.finishedWidth(),
                request.finishedWidthUnit(),
                request.customerId()));
    Optional<BigDecimal> canonicalTarget =
        stockQuery.toCanonical(request.requestedQty(), request.unit(), dimension);
    if (canonicalTarget.isEmpty()) {
      // The lots stay readable in the canonical unit; the options cannot be stated in the line
      // unit.
      return new EvaluationRun(
          unknown(request, canonicalUnit, "UNIT_NOT_CONVERTIBLE"), dimension, canonicalUnit, stock);
    }
    Function<BigDecimal, BigDecimal> toLineUnit =
        canonical ->
            stockQuery
                .fromCanonical(canonical, request.unit(), dimension)
                .map(value -> value.setScale(3, RoundingMode.HALF_UP))
                .orElse(canonical);
    QuantityEvaluator.Evaluation evaluation =
        QuantityEvaluator.evaluate(
            new QuantityEvaluator.Input(
                request.requestedQty(),
                request.unit(),
                canonicalUnit,
                canonicalTarget.get(),
                toLineUnit,
                stock.lots(),
                stock.confirmedCompatibleGroups(),
                request.toneGroups(),
                request.singleLotRequired(),
                request.toleranceUpPct(),
                request.toleranceDownPct()));
    return new EvaluationRun(evaluation, dimension, canonicalUnit, stock);
  }

  private QuantityEvaluator.Evaluation unknown(
      EvaluationRequest request, String canonicalUnit, String reason) {
    QuantityEvaluationResult result =
        new QuantityEvaluationResult(
            QuantityEvaluationResult.EvaluationStatus.UNKNOWN,
            request.requestedQty(),
            request.unit(),
            canonicalUnit,
            List.of(),
            0,
            0,
            0,
            List.of(reason),
            false,
            0);
    // Seeded with the saved line's id exactly as before; a preview has none and never exposes it.
    return new QuantityEvaluator.Evaluation(
        result,
        QuantityEvaluator.sha256(request.lineId() + "|" + reason + "|" + request.requestedQty()));
  }

  /**
   * What one evaluation needs. {@code lineId} is the saved line (it seeds the fingerprint of an
   * unknown result) and is null for a preview. Tolerances are on the percent scale the line stores.
   */
  public record EvaluationRequest(
      UUID lineId,
      UUID productId,
      UUID colorId,
      BigDecimal finishedWidth,
      String finishedWidthUnit,
      UUID customerId,
      BigDecimal requestedQty,
      String unit,
      boolean singleLotRequired,
      BigDecimal toleranceUpPct,
      BigDecimal toleranceDownPct,
      List<Set<UUID>> toneGroups) {

    public EvaluationRequest {
      toneGroups = toneGroups == null ? List.of() : List.copyOf(toneGroups);
    }
  }

  /**
   * An evaluation with the stock read it was computed from. {@code measure} is null and the stock
   * is empty when the product's measure is unknown; the canonical unit is then {@code "?"}.
   */
  public record EvaluationRun(
      QuantityEvaluator.Evaluation evaluation,
      PrimaryMeasure measure,
      String canonicalUnit,
      ProposalStockQueryService.ProposalStock stock) {}
}
