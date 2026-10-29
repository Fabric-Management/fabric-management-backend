package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.inventory.lothealth.app.port.LotHealthSettingsPort;
import com.fabricmanagement.inventory.lothealth.app.port.LotHealthSettingsUnavailableException;
import com.fabricmanagement.inventory.lothealth.app.port.ProductSpeedPort;
import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.CustomerToneAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalPolicyCode;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.QuantityProposalDto;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerToneAcceptanceRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityProposalRepository;
import com.fabricmanagement.sales.quote.app.QuoteScopeQueryService;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates a line's requested quantity against whole pieces in stock and records the proposal (SOI
 * D2, STOCK-PROPOSAL-2). Reads stock and what quotes hold; reserves nothing; never changes the
 * line.
 */
@Service
@RequiredArgsConstructor
public class QuantityEvaluationService {

  private static final String UNKNOWN_CANONICAL_UNIT = "?";

  private static final ProposalStockQueryService.ProposalStock NO_STOCK =
      new ProposalStockQueryService.ProposalStock(List.of(), List.of());

  private static final QuoteHoldQueryService.QuoteHolds NO_HOLDS =
      new QuoteHoldQueryService.QuoteHolds(List.of(), List.of());

  private final OrderIntakeAccess access;
  private final ProductSalesDefinitionQueryService productDefinitions;
  private final ProposalStockQueryService stockQuery;
  private final QuoteHoldQueryService quoteHolds;
  private final QuoteScopeQueryService quoteScopes;
  private final CustomerToneAcceptanceRepository toneAcceptances;
  private final QuantityProposalRepository proposals;
  private final Clock clock;
  private final LotHealthSettingsPort lotHealthSettings;
  private final ProductSpeedPort productSpeed;

  @Transactional
  public QuantityProposalDto evaluate(UUID orderId, UUID lineId, UUID actor) {
    return evaluate(orderId, lineId, List.of(), actor);
  }

  /**
   * Evaluates and records the line's proposal. {@code manualPieceIds}, when given, is the
   * salesperson's own pick, evaluated as one more option with the same checks.
   */
  @Transactional
  public QuantityProposalDto evaluate(
      UUID orderId, UUID lineId, List<UUID> manualPieceIds, UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    if (order.getStatus() != OrderStatus.DRAFT) {
      throw OrderIntakeException.conflict(
          "ORDER_NOT_DRAFT", "Quantities are evaluated while the order is a draft");
    }
    SalesOrderLine line = access.line(order, lineId);
    QuantityProposal proposal = evaluateAndRecord(order, line, manualPieceIds, actor);
    return QuantityProposalDto.from(proposal, readableQuotes(proposal.getResult(), actor));
  }

  @Transactional(readOnly = true)
  public Optional<QuantityProposalDto> latest(UUID orderId, UUID lineId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    SalesOrderLine line = access.line(order, lineId);
    return proposals
        .findFirstByTenantIdAndSalesOrderLineIdOrderByEvaluatedAtDescIdDesc(
            TenantContext.requireTenantId(), line.getId())
        .map(
            proposal ->
                QuantityProposalDto.from(proposal, readableQuotes(proposal.getResult(), actor)));
  }

  /**
   * Quotes named by the result's held stock that the viewer may read. Other quotes and their
   * marketers stay unnamed in what the viewer is shown.
   */
  public Set<UUID> readableQuotes(QuantityEvaluationResult result, UUID actor) {
    Set<UUID> quoteIds =
        result.options().stream()
            .flatMap(option -> option.heldStock().stream())
            .map(QuantityOption.HeldStock::quoteId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    return quoteIds.isEmpty()
        ? Set.of()
        : quoteScopes.findReadableQuoteIds(TenantContext.requireTenantId(), actor, quoteIds);
  }

  /**
   * Re-evaluates without persisting. Used by acceptance and confirmation to detect that stock
   * changed since the proposal the customer accepted.
   */
  @Transactional(readOnly = true)
  public QuantityEvaluator.Evaluation reevaluate(SalesOrder order, SalesOrderLine line) {
    return evaluation(order, line, List.of());
  }

  QuantityProposal evaluateAndRecord(
      SalesOrder order, SalesOrderLine line, List<UUID> manualPieceIds, UUID actor) {
    QuantityEvaluator.Evaluation evaluation = evaluation(order, line, manualPieceIds);
    return proposals.save(
        QuantityProposal.record(
            order.getId(),
            line.getId(),
            line.getProductId(),
            line.getCustomerRequestQty(),
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
            toneGroups(tenantId, order, line),
            !line.isSingleLotRequired()));
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
  /**
   * The line's current stock. {@code multipleLotsAllowed}: the line allows several dye lots, so the
   * customer accepts them (Fatih, 2026-10-15).
   */
  public record LineStock(
      PrimaryMeasure measure,
      List<ProposalStockQueryService.ProposalLot> lots,
      List<Set<UUID>> confirmedGroups,
      List<Set<UUID>> toneGroups,
      boolean multipleLotsAllowed) {}

  private QuantityEvaluator.Evaluation evaluation(
      SalesOrder order, SalesOrderLine line, List<UUID> manualPieceIds) {
    UUID tenantId = TenantContext.requireTenantId();
    return run(new EvaluationRequest(
            line.getId(),
            line.getProductId(),
            line.getColorId(),
            line.getFinishedWidth(),
            line.getFinishedWidthUnit(),
            order.getTradingPartnerId(),
            line.getCustomerRequestQty(),
            line.getUnit(),
            line.isSingleLotRequired(),
            line.getToleranceUpPct(),
            line.getToleranceDownPct(),
            toneGroups(tenantId, order, line),
            manualPieceIds))
        .evaluation();
  }

  /**
   * The one evaluation path (STOCK-PREVIEW-1 A1, STOCK-PROPOSAL-2): a saved line and an unsaved
   * preview are evaluated by the same evaluator, from the same stock and quote-hold reads and with
   * the same unit conversion. Stock and holds are read once, as soon as the product's measure is
   * known, and returned with the result so a caller can describe exactly the lots the options were
   * computed from. A manual pick naming pieces that are not offerable stock is refused as stale.
   * Persists nothing.
   *
   * <p>Public, not package-visible, because callers reach it through this bean's class-based
   * transaction proxy.
   */
  public EvaluationRun run(EvaluationRequest request) {
    return runWithOwnedSources(request, Set.of());
  }

  /** The verified target's existing hard allocations stay usable when replanning a cut. */
  public EvaluationRun runForCut(EvaluationRequest request, Set<UUID> allocatedToVerifiedTarget) {
    return runWithOwnedSources(request, Set.copyOf(allocatedToVerifiedTarget));
  }

  public java.util.Map<UUID, BigDecimal> freeCapacitiesForCut(
      EvaluationRequest request, Set<UUID> owned) {
    var run = runForCut(request, owned);
    var evidence = QuoteHoldEvidence.of(run.holds());
    java.util.Map<UUID, BigDecimal> capacities = new java.util.HashMap<>();
    for (var lot : run.stock().lots()) {
      var free = StockPools.lotFree(evidence, lot).canonical();
      if (free != null) capacities.put(lot.batchId(), free);
    }
    return java.util.Map.copyOf(capacities);
  }

  private EvaluationRun runWithOwnedSources(EvaluationRequest request, Set<UUID> owned) {
    // Both limits are required (Fatih, 2026-10-13): a missing side is missing information, never
    // 0%. No proposal is computed, so no earlier one is presented as current.
    if (request.toleranceUpPct() == null || request.toleranceDownPct() == null) {
      throw OrderIntakeException.rule(
          "TOLERANCE_REQUIRED",
          "Enter both the upper and the lower tolerance; write 0% when no deviation is accepted");
    }
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
          NO_STOCK,
          NO_HOLDS);
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
    if (!owned.isEmpty()) {
      stock =
          new ProposalStockQueryService.ProposalStock(
              stock.lots().stream()
                  .map(
                      lot ->
                          new ProposalStockQueryService.ProposalLot(
                              lot.batchId(),
                              lot.lotNo(),
                              lot.productionDate(),
                              lot.measure(),
                              lot.canonicalUnit(),
                              lot.widthEvidence(),
                              lot.pieces().stream()
                                  .map(
                                      piece ->
                                          owned.contains(piece.stockUnitId())
                                                  && "ALLOCATED".equals(piece.reason())
                                              ? new ProposalStockQueryService.ProposalPiece(
                                                  piece.stockUnitId(),
                                                  piece.pieceNo(),
                                                  piece.canonicalMeasure(),
                                                  ProposalStockQueryService.PieceState.ELIGIBLE,
                                                  null,
                                                  piece.version(),
                                                  piece.packageType(),
                                                  piece.initialCanonicalMeasure())
                                              : piece)
                                  .toList(),
                              lot.averagePiece(),
                              lot.averageBasis(),
                              lot.inventoryComplete()))
                  .toList(),
              stock.confirmedCompatibleGroups());
    }
    QuoteHoldQueryService.QuoteHolds holds =
        stock.lots().isEmpty()
            ? NO_HOLDS
            : quoteHolds.find(
                stock.lots().stream().map(ProposalStockQueryService.ProposalLot::batchId).toList(),
                request.ownSessionId(),
                request.lineId());
    Set<UUID> ownOwners =
        java.util.stream.Stream.of(request.lineId(), request.ownSessionId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    holds = holds.excludingOwners(ownOwners);
    Optional<BigDecimal> canonicalTarget =
        stockQuery.toCanonical(request.requestedQty(), request.unit(), dimension);
    if (canonicalTarget.isEmpty()) {
      // The lots stay readable in the canonical unit; the options cannot be stated in the line
      // unit.
      return new EvaluationRun(
          unknown(request, canonicalUnit, "UNIT_NOT_CONVERTIBLE"),
          dimension,
          canonicalUnit,
          stock,
          holds);
    }
    Function<BigDecimal, BigDecimal> toLineUnit =
        canonical ->
            stockQuery
                .fromCanonical(canonical, request.unit(), dimension)
                .map(value -> value.setScale(3, RoundingMode.HALF_UP))
                .orElse(canonical);
    boolean fabricRolls =
        product.productType() == ProductType.FABRIC && dimension == PrimaryMeasure.LENGTH;
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
                request.toleranceDownPct(),
                QuoteHoldEvidence.of(holds),
                request.manualPieceIds(),
                fabricRolls ? ProposalPolicyCode.FABRIC_ROLL_BASE : ProposalPolicyCode.STANDARD),
            lotHealthPolicy(tenantId, request.productId(), request.colorId(), fabricRolls));
    if (!evaluation.manualUnavailablePieceIds().isEmpty()) {
      throw OrderIntakeException.conflict(
          "MANUAL_PIECES_UNAVAILABLE",
          "Some picked pieces are no longer offerable stock; evaluate again: "
              + evaluation.manualUnavailablePieceIds());
    }
    return new EvaluationRun(evaluation, dimension, canonicalUnit, stock, holds);
  }

  /**
   * The tenant's lot-health rule for the product at its speed (Fatih, 2026-10-14): the slower it
   * sells, the more normal pieces a lot keeps.
   */
  private LotHealthPolicy lotHealthPolicy(
      UUID tenantId, UUID productId, UUID colorId, boolean fabricRolls) {
    try {
      var settings = lotHealthSettings.forTenant(tenantId);
      return fabricRolls
          ? settings.fabricRollPolicy()
          : settings.policyFor(productSpeed.speedOf(tenantId, productId, colorId));
    } catch (LotHealthSettingsUnavailableException e) {
      // Never propose on the standard when the tenant's own threshold may be stricter.
      throw OrderIntakeException.conflict(
          "SETTINGS_UNAVAILABLE",
          "The stock-proposal settings cannot be read now; evaluate again later");
    }
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
            null,
            true,
            0,
            0,
            0,
            List.of(reason),
            false);
    // Seeded with the saved line's id exactly as before; a preview has none and never exposes it.
    return new QuantityEvaluator.Evaluation(
        result,
        QuantityEvaluator.sha256(request.lineId() + "|" + reason + "|" + request.requestedQty()));
  }

  /**
   * What one evaluation needs. {@code lineId} is the saved line (it seeds the fingerprint of an
   * unknown result) and is null for a preview. Tolerances are on the percent scale the line stores.
   * {@code manualPieceIds} is the salesperson's own pick; empty for none.
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
      List<Set<UUID>> toneGroups,
      List<UUID> manualPieceIds,
      UUID ownSessionId) {

    public EvaluationRequest(
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
        List<Set<UUID>> toneGroups,
        List<UUID> manualPieceIds) {
      this(
          lineId,
          productId,
          colorId,
          finishedWidth,
          finishedWidthUnit,
          customerId,
          requestedQty,
          unit,
          singleLotRequired,
          toleranceUpPct,
          toleranceDownPct,
          toneGroups,
          manualPieceIds,
          null);
    }

    public EvaluationRequest {
      toneGroups = toneGroups == null ? List.of() : List.copyOf(toneGroups);
      manualPieceIds =
          manualPieceIds == null ? List.of() : manualPieceIds.stream().distinct().toList();
    }
  }

  /**
   * An evaluation with the stock and quote-hold reads it was computed from. {@code measure} is null
   * and the stock is empty when the product's measure is unknown; the canonical unit is then {@code
   * "?"}.
   */
  public record EvaluationRun(
      QuantityEvaluator.Evaluation evaluation,
      PrimaryMeasure measure,
      String canonicalUnit,
      ProposalStockQueryService.ProposalStock stock,
      QuoteHoldQueryService.QuoteHolds holds) {}
}
