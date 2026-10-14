package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.BlockedReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.Compatibility;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.HeldStock;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionKind;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionPlacement;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockSource;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Builds an option from lot takes (STOCK-PROPOSAL-2): quantities in the line unit, the tolerance,
 * each lot's part with what it keeps, and what other quotes hold on it.
 */
final class OptionBuilder {

  private OptionBuilder() {}

  /**
   * An option from lot takes. {@code compatibilityBlock} is the prerequisite block, if any; a take
   * of held stock blocks the option as well.
   */
  static QuantityOption option(
      EvaluationContext context,
      String key,
      List<WholePieceSearch.LotTake> takes,
      Compatibility compatibility,
      BlockedReason compatibilityBlock) {
    List<QuantityOption.LotPart> parts = new ArrayList<>();
    List<HeldStock> held = new ArrayList<>();
    boolean touchesVerified = false;
    boolean touchesUnverified = false;
    BigDecimal canonical = BigDecimal.ZERO;
    for (WholePieceSearch.LotTake take : takes) {
      ProposalLot lot = context.lotsById().get(take.lotId());
      Set<UUID> taken = Set.copyOf(take.pieceIds());
      BigDecimal lotCanonical =
          take.pieceIds().stream()
              .map(id -> context.piecesById().get(id).canonicalMeasure())
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      canonical = canonical.add(lotCanonical);
      parts.add(part(context, lot, take, taken, lotCanonical));

      HeldStockView.HeldOnLot onLot = HeldStockView.heldOnLot(context, lot, taken);
      held.addAll(onLot.entries());
      touchesVerified |= onLot.verified();
      touchesUnverified |= onLot.unverified();
    }
    BigDecimal requested = context.input().requestedQuantity();
    BigDecimal quantity = context.toLineUnit(canonical);
    BigDecimal difference = quantity.subtract(requested);
    BigDecimal percent =
        difference.multiply(EvaluationContext.HUNDRED).divide(requested, 1, RoundingMode.HALF_UP);
    boolean remnant = parts.stream().anyMatch(QuantityOption.LotPart::remnant);
    OptionKind kind =
        difference.signum() == 0
            ? remnant ? OptionKind.REQUESTED_WITH_REMNANT : OptionKind.EXACT
            : difference.signum() > 0 ? OptionKind.ABOVE : OptionKind.BELOW;
    BlockedReason blocked = compatibilityBlock;
    if (blocked == null && touchesVerified) {
      blocked = BlockedReason.HELD_BY_ANOTHER_QUOTE;
    } else if (blocked == null && touchesUnverified) {
      blocked = BlockedReason.HOLD_UNVERIFIED;
    }
    return new QuantityOption(
        key,
        OptionPlacement.ABOVE,
        kind,
        compatibility,
        touchesVerified || touchesUnverified ? StockSource.HELD : StockSource.FREE,
        quantity,
        canonical,
        difference,
        percent,
        context.tolerance(difference),
        List.of(),
        parts,
        held,
        blocked);
  }

  private static QuantityOption.LotPart part(
      EvaluationContext context,
      ProposalLot lot,
      WholePieceSearch.LotTake take,
      Set<UUID> taken,
      BigDecimal lotCanonical) {
    List<ProposalPiece> remainingEligible =
        lot.pieces().stream()
            .filter(piece -> piece.state() == PieceState.ELIGIBLE)
            .filter(piece -> !taken.contains(piece.stockUnitId()))
            .toList();
    BigDecimal remainingCanonical =
        remainingEligible.stream()
            .map(ProposalPiece::canonicalMeasure)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    int unknownInLot =
        (int) lot.pieces().stream().filter(piece -> piece.state() == PieceState.UNKNOWN).count();
    // Fewer pool pieces left than the lot-health minimum (Fatih, 2026-10-13 and 2026-10-14); free
    // pieces first, as the free pool is the one searched. Pieces of unknown state are never
    // counted: they are not offered.
    boolean remnant = context.lotHealth().leavesRemnant(take.remainingPieces());
    List<ProposalPiece> remnantPieces =
        !remnant
            ? List.of()
            : remainingEligible.stream()
                .sorted(
                    Comparator.comparing(
                        (ProposalPiece piece) ->
                            context.input().holds().pieceHolds().containsKey(piece.stockUnitId())))
                .limit(take.remainingPieces())
                .toList();
    BigDecimal remnantCanonical =
        remnantPieces.stream()
            .map(ProposalPiece::canonicalMeasure)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean closes = lot.pieces().stream().allMatch(piece -> taken.contains(piece.stockUnitId()));
    return new QuantityOption.LotPart(
        lot.batchId(),
        lot.lotNo(),
        lot.productionDate(),
        take.pieceIds(),
        context.toLineUnit(lotCanonical),
        remainingEligible.size(),
        context.toLineUnit(remainingCanonical),
        unknownInLot,
        remnant,
        remnantPieces.stream().map(ProposalPiece::stockUnitId).toList(),
        remnant ? context.toLineUnit(remnantCanonical) : null,
        closes);
  }
}
