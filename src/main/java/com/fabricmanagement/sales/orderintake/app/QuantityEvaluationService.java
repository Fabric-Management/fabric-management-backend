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
    ProductSalesDefinitionDto product =
        productDefinitions
            .find(tenantId, line.getProductId())
            .orElseThrow(() -> OrderIntakeException.productNotAvailable(line.getProductId()));
    Optional<PrimaryMeasure> measure = stockQuery.measureFor(product.productType());
    if (measure.isEmpty()) {
      return unknown(line, "?", "MEASURE_UNKNOWN");
    }
    String canonicalUnit = stockQuery.canonicalUnit(measure.get());
    Optional<BigDecimal> canonicalTarget =
        stockQuery.toCanonical(line.getRequestedQty(), line.getUnit(), measure.get());
    if (canonicalTarget.isEmpty()) {
      return unknown(line, canonicalUnit, "UNIT_NOT_CONVERTIBLE");
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
    List<Set<UUID>> toneGroups = toneGroups(tenantId, order, line);
    PrimaryMeasure dimension = measure.get();
    Function<BigDecimal, BigDecimal> toLineUnit =
        canonical ->
            stockQuery
                .fromCanonical(canonical, line.getUnit(), dimension)
                .map(value -> value.setScale(3, RoundingMode.HALF_UP))
                .orElse(canonical);
    return QuantityEvaluator.evaluate(
        new QuantityEvaluator.Input(
            line.getRequestedQty(),
            line.getUnit(),
            canonicalUnit,
            canonicalTarget.get(),
            toLineUnit,
            stock.lots(),
            stock.confirmedCompatibleGroups(),
            toneGroups,
            line.isSingleLotRequired(),
            order.getAgreedToleranceUpPct(),
            order.getAgreedToleranceDownPct()));
  }

  private QuantityEvaluator.Evaluation unknown(
      SalesOrderLine line, String canonicalUnit, String reason) {
    QuantityEvaluationResult result =
        new QuantityEvaluationResult(
            QuantityEvaluationResult.EvaluationStatus.UNKNOWN,
            line.getRequestedQty(),
            line.getUnit(),
            canonicalUnit,
            List.of(),
            0,
            0,
            0,
            List.of(reason),
            false,
            0);
    return new QuantityEvaluator.Evaluation(
        result,
        QuantityEvaluator.sha256(line.getId() + "|" + reason + "|" + line.getRequestedQty()));
  }
}
