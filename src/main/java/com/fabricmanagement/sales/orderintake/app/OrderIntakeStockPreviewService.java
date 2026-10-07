package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.HeldPieceView;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.LotIntentView;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.QuoteHolds;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto.FreeIncompleteReason;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto.HoldStatus;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto.Quantity;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest;
import com.fabricmanagement.sales.quote.app.QuoteScopeQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Current stock for an order line that is not saved yet (STOCK-PREVIEW-1 A3). The evaluation comes
 * from {@link QuantityEvaluationService#run}, the path a saved line takes, and the lot view is
 * built from the very stock read that evaluation used. What quotes hold is read from production and
 * shown beside the options; the options themselves are never changed (D3). Reserves, records and
 * sends nothing.
 *
 * <p>A piece hold is VERIFIED only when its quote line has an active intent on the same lot; its
 * quantity is then already in that intent and is not subtracted again. Any other hold record is
 * UNVERIFIED: it names no quote, is not subtracted as a commitment, and makes the lot's free
 * quantity undeterminable rather than a guessed number.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderIntakeStockPreviewService {

  private static final int LINE_UNIT_SCALE = 3;

  private final QuantityEvaluationService evaluations;
  private final ProposalStockQueryService stockQuery;
  private final QuoteHoldQueryService quoteHolds;
  private final QuoteScopeQueryService quoteScopes;

  public OrderIntakeStockPreviewDto preview(OrderIntakeStockPreviewRequest request, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    QuantityEvaluationService.EvaluationRun run =
        evaluations.run(
            new QuantityEvaluationService.EvaluationRequest(
                null,
                request.productId(),
                request.colorId(),
                request.finishedWidth(),
                request.finishedWidthUnit(),
                request.customerId(),
                request.requestedQty(),
                request.unit(),
                Boolean.TRUE.equals(request.singleLotRequired()),
                request.toleranceUpPct(),
                request.toleranceDownPct(),
                List.of()));
    List<ProposalLot> stockLots = run.stock().lots();
    QuoteHolds holds =
        stockLots.isEmpty()
            ? new QuoteHolds(List.of(), List.of())
            : quoteHolds.find(stockLots.stream().map(ProposalLot::batchId).toList());
    Set<UUID> readableQuotes = readableQuotes(tenantId, actor, holds.intents());
    Units units = new Units(stockQuery, run.measure(), request.unit());

    Map<UUID, List<LotIntentView>> intentsByLot =
        holds.intents().stream().collect(Collectors.groupingBy(LotIntentView::batchId));
    Map<UUID, List<HeldPieceView>> holdsByLot =
        holds.heldPieces().stream().collect(Collectors.groupingBy(HeldPieceView::batchId));
    List<LotFacts> lots =
        stockLots.stream()
            .map(
                lot ->
                    LotFacts.of(
                        lot,
                        intentsByLot.getOrDefault(lot.batchId(), List.of()),
                        holdsByLot.getOrDefault(lot.batchId(), List.of())))
            .toList();

    return new OrderIntakeStockPreviewDto(
        request.unit(),
        run.canonicalUnit(),
        OrderIntakeStockPreviewDto.Evaluation.from(run.evaluation().result()),
        lots.stream().map(facts -> toLot(facts, units, readableQuotes)).toList(),
        optionChecks(run, lots),
        totals(request, run, lots, units),
        lots.stream().mapToInt(LotFacts::unconvertibleIntents).sum());
  }

  private Set<UUID> readableQuotes(UUID tenantId, UUID actor, List<LotIntentView> intents) {
    Set<UUID> quoteIds =
        intents.stream()
            .map(LotIntentView::quoteId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    return quoteIds.isEmpty()
        ? Set.of()
        : quoteScopes.findReadableQuoteIds(tenantId, actor, quoteIds);
  }

  private OrderIntakeStockPreviewDto.Lot toLot(
      LotFacts facts, Units units, Set<UUID> readableQuotes) {
    return new OrderIntakeStockPreviewDto.Lot(
        facts.lot().batchId(),
        facts.lot().lotNo(),
        facts.eligiblePieces(),
        facts.unknownPieces(),
        facts.unmeasuredPieces(),
        units.quantity(facts.eligible()),
        units.quantity(facts.unknown()),
        units.quantity(facts.heldByQuotes()),
        units.quantity(facts.unverifiedHold()),
        facts.unverifiedHoldPieces(),
        facts.free() == null ? null : units.quantity(facts.free()),
        facts.reasons().isEmpty(),
        List.copyOf(facts.reasons()),
        facts.intents().stream()
            .map(
                intent -> {
                  boolean readable = readableQuotes.contains(intent.quoteId());
                  return new OrderIntakeStockPreviewDto.Intent(
                      readable ? intent.quoteNumber() : null,
                      readable ? intent.marketerName() : null,
                      intent.quantity(),
                      intent.unit(),
                      intent.canonicalQuantity(),
                      intent.expiresAt());
                })
            .toList(),
        facts.holds().stream().map(hold -> toHeldPiece(hold, facts, readableQuotes)).toList());
  }

  private OrderIntakeStockPreviewDto.HeldPiece toHeldPiece(
      HeldPieceView hold, LotFacts facts, Set<UUID> readableQuotes) {
    String pieceNo = facts.pieceNumbers().get(hold.stockUnitId());
    Optional<LotIntentView> backing = facts.backingIntent(hold);
    if (backing.isEmpty()) {
      // Not evidence of a live quote hold; no quote is named or looked up for it.
      return new OrderIntakeStockPreviewDto.HeldPiece(
          hold.stockUnitId(), pieceNo, HoldStatus.UNVERIFIED, null, null, null);
    }
    LotIntentView intent = backing.get();
    boolean readable = readableQuotes.contains(intent.quoteId());
    return new OrderIntakeStockPreviewDto.HeldPiece(
        hold.stockUnitId(),
        pieceNo,
        HoldStatus.VERIFIED,
        readable ? intent.quoteNumber() : null,
        readable ? intent.marketerName() : null,
        intent.expiresAt());
  }

  private List<OrderIntakeStockPreviewDto.OptionCheck> optionChecks(
      QuantityEvaluationService.EvaluationRun run, List<LotFacts> lots) {
    Map<UUID, LotFacts> lotsById =
        lots.stream().collect(Collectors.toMap(facts -> facts.lot().batchId(), facts -> facts));
    Map<UUID, BigDecimal> pieceMeasures = new HashMap<>();
    lots.forEach(
        facts ->
            facts.lot().pieces().stream()
                .filter(piece -> piece.canonicalMeasure() != null)
                .forEach(
                    piece -> pieceMeasures.put(piece.stockUnitId(), piece.canonicalMeasure())));
    return run.evaluation().result().options().stream()
        .map(option -> optionCheck(option, lotsById, pieceMeasures))
        .toList();
  }

  private OrderIntakeStockPreviewDto.OptionCheck optionCheck(
      QuantityOption option, Map<UUID, LotFacts> lotsById, Map<UUID, BigDecimal> pieceMeasures) {
    List<OrderIntakeStockPreviewDto.OptionLotCheck> parts =
        option.lots().stream().map(part -> partCheck(part, lotsById, pieceMeasures)).toList();
    boolean covered =
        parts.stream()
            .allMatch(
                part ->
                    Boolean.FALSE.equals(part.exceedsFree())
                        && part.heldPieceIds().isEmpty()
                        && part.unverifiedHoldPieceIds().isEmpty());
    return new OrderIntakeStockPreviewDto.OptionCheck(option.optionKey(), covered, parts);
  }

  /**
   * The two separate checks of one lot part: (a) it takes more than the lot's free quantity, (b) it
   * uses pieces held by a quote. The part's quantity is summed from its pieces' canonical measures,
   * never read from the option, whose line-unit figure falls back to the canonical value when the
   * units do not convert.
   */
  private OrderIntakeStockPreviewDto.OptionLotCheck partCheck(
      QuantityOption.LotPart part,
      Map<UUID, LotFacts> lotsById,
      Map<UUID, BigDecimal> pieceMeasures) {
    LotFacts facts = lotsById.get(part.batchId());
    List<UUID> pieceIds = part.pieceIds();
    BigDecimal partQuantity =
        pieceIds.stream()
            .map(pieceMeasures::get)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Boolean exceedsFree =
        facts == null || facts.free() == null ? null : partQuantity.compareTo(facts.free()) > 0;
    List<UUID> held =
        facts == null
            ? List.of()
            : pieceIds.stream().filter(facts.verifiedHoldPieceIds()::contains).toList();
    List<UUID> unverified =
        facts == null
            ? List.of()
            : pieceIds.stream().filter(facts.unverifiedHoldPieceIds()::contains).toList();
    return new OrderIntakeStockPreviewDto.OptionLotCheck(
        part.batchId(), exceedsFree, held, unverified);
  }

  private OrderIntakeStockPreviewDto.Totals totals(
      OrderIntakeStockPreviewRequest request,
      QuantityEvaluationService.EvaluationRun run,
      List<LotFacts> lots,
      Units units) {
    EnumSet<FreeIncompleteReason> reasons = EnumSet.noneOf(FreeIncompleteReason.class);
    lots.forEach(facts -> reasons.addAll(facts.reasons()));
    // An unknown measure leaves nothing to compute; the evaluation's reason explains why.
    boolean freeComplete = run.measure() != null && reasons.isEmpty();
    BigDecimal free = freeComplete ? sum(lots, LotFacts::free) : null;
    // Only ready free stock covers the request. Stock awaiting suitability, unmeasured records and
    // work in progress never reduce what is left, and this says nothing about production.
    Quantity notCoveredByReadyStock = null;
    if (free != null) {
      notCoveredByReadyStock =
          stockQuery
              .toCanonical(request.requestedQty(), request.unit(), run.measure())
              .map(target -> target.subtract(free).max(BigDecimal.ZERO))
              .map(units::quantity)
              .orElse(null);
    }
    return new OrderIntakeStockPreviewDto.Totals(
        free == null ? null : units.quantity(free),
        units.quantity(sum(lots, LotFacts::unknown)),
        units.quantity(sum(lots, LotFacts::heldByQuotes)),
        units.quantity(sum(lots, LotFacts::unverifiedHold)),
        notCoveredByReadyStock,
        freeComplete,
        List.copyOf(reasons));
  }

  private static BigDecimal sum(List<LotFacts> lots, Function<LotFacts, BigDecimal> value) {
    return lots.stream()
        .map(value)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * States a canonical quantity in the line's unit as the evaluation does (3 decimals); null in the
   * line's unit when the measure is unknown or the units do not convert.
   */
  private record Units(
      ProposalStockQueryService stockQuery, PrimaryMeasure measure, String lineUnit) {

    Quantity quantity(BigDecimal canonical) {
      BigDecimal inLineUnit =
          measure == null
              ? null
              : stockQuery
                  .fromCanonical(canonical, lineUnit, measure)
                  .map(value -> value.setScale(LINE_UNIT_SCALE, RoundingMode.HALF_UP))
                  .orElse(null);
      return new Quantity(canonical, inLineUnit);
    }
  }

  /** What is known about one lot, in its canonical unit. */
  private record LotFacts(
      ProposalLot lot,
      List<LotIntentView> intents,
      List<HeldPieceView> holds,
      Map<UUID, String> pieceNumbers,
      int eligiblePieces,
      int unknownPieces,
      int unmeasuredPieces,
      BigDecimal eligible,
      BigDecimal unknown,
      BigDecimal heldByQuotes,
      int unconvertibleIntents,
      Set<UUID> verifiedHoldPieceIds,
      Set<UUID> unverifiedHoldPieceIds,
      BigDecimal unverifiedHold,
      int unverifiedHoldPieces,
      BigDecimal free,
      EnumSet<FreeIncompleteReason> reasons) {

    static LotFacts of(ProposalLot lot, List<LotIntentView> intents, List<HeldPieceView> allHolds) {
      Map<UUID, String> pieceNumbers = new HashMap<>();
      lot.pieces().forEach(piece -> pieceNumbers.put(piece.stockUnitId(), piece.pieceNo()));
      // A hold on a piece outside the current stock (depleted, disposed) cannot be offered and is
      // left out.
      List<HeldPieceView> holds =
          allHolds.stream().filter(hold -> pieceNumbers.containsKey(hold.stockUnitId())).toList();
      Set<UUID> intentLines =
          intents.stream().map(LotIntentView::quoteLineId).collect(Collectors.toSet());
      Set<UUID> verified = new LinkedHashSet<>();
      Set<UUID> unverified = new LinkedHashSet<>();
      holds.forEach(
          hold ->
              (intentLines.contains(hold.quoteLineId()) ? verified : unverified)
                  .add(hold.stockUnitId()));

      List<ProposalPiece> eligiblePieces = pieces(lot, PieceState.ELIGIBLE);
      List<ProposalPiece> unknownPieces = pieces(lot, PieceState.UNKNOWN);
      List<ProposalPiece> unverifiedEligible =
          eligiblePieces.stream()
              .filter(piece -> unverified.contains(piece.stockUnitId()))
              .toList();
      BigDecimal eligible = measured(eligiblePieces);
      BigDecimal heldByQuotes =
          intents.stream()
              .map(LotIntentView::canonicalQuantity)
              .filter(Objects::nonNull)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      int unconvertible =
          (int) intents.stream().filter(intent -> intent.canonicalQuantity() == null).count();

      // Only a hold on an eligible piece can change what is free; holds on other pieces are still
      // listed but do not make the free quantity undeterminable.
      EnumSet<FreeIncompleteReason> reasons = EnumSet.noneOf(FreeIncompleteReason.class);
      if (unconvertible > 0) {
        reasons.add(FreeIncompleteReason.UNCONVERTIBLE_INTENT);
      }
      if (!unverifiedEligible.isEmpty()) {
        reasons.add(FreeIncompleteReason.UNVERIFIED_HOLD);
      }
      BigDecimal free =
          reasons.isEmpty() ? eligible.subtract(heldByQuotes).max(BigDecimal.ZERO) : null;

      return new LotFacts(
          lot,
          List.copyOf(intents),
          holds,
          pieceNumbers,
          eligiblePieces.size(),
          unknownPieces.size(),
          (int) unknownPieces.stream().filter(piece -> piece.canonicalMeasure() == null).count(),
          eligible,
          measured(unknownPieces),
          heldByQuotes,
          unconvertible,
          Set.copyOf(verified),
          Set.copyOf(unverified),
          measured(unverifiedEligible),
          unverifiedEligible.size(),
          free,
          reasons);
    }

    /** The active intent of the hold's quote line on this lot, if any. */
    Optional<LotIntentView> backingIntent(HeldPieceView hold) {
      return intents.stream()
          .filter(intent -> Objects.equals(intent.quoteLineId(), hold.quoteLineId()))
          .findFirst();
    }

    private static List<ProposalPiece> pieces(ProposalLot lot, PieceState state) {
      return lot.pieces().stream().filter(piece -> piece.state() == state).toList();
    }

    private static BigDecimal measured(Collection<ProposalPiece> pieces) {
      return pieces.stream()
          .map(ProposalPiece::canonicalMeasure)
          .filter(Objects::nonNull)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
  }
}
