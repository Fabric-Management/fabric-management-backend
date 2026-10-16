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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Builds an option from lot takes (STOCK-PROPOSAL-2): quantities in the line unit, the tolerance,
 * each lot's part with what it keeps, and what other quotes hold on it. Short pieces are applied
 * silently and never named (Fatih, 2026-10-14).
 */
final class OptionBuilder {

  private OptionBuilder() {}

  /**
   * An option from lot takes. {@code pools} are the lots the takes were chosen from: what each lot
   * keeps is read from its pool. {@code compatibilityBlock} is the prerequisite block, if any; a
   * take of held stock blocks the option as well.
   */
  static QuantityOption option(
      EvaluationContext context,
      Map<UUID, WholePieceSearch.Lot> pools,
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
      parts.add(part(context, lot, pools.get(take.lotId()), take, taken, lotCanonical));

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
      WholePieceSearch.Lot pool,
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
    // What the pool keeps (Fatih, 2026-10-13 and 2026-10-14): a remnant when some pieces stay but
    // fewer normal ones than the lot-health minimum; short pieces do not count. Pieces of unknown
    // state are never counted: they are not offered.
    List<WholePieceSearch.Piece> remainingPool =
        pool.pieces().stream().filter(piece -> !taken.contains(piece.id())).toList();
    long normalLeft = remainingPool.stream().filter(piece -> !piece.shortPiece()).count();
    boolean remnant =
        !remainingPool.isEmpty() && normalLeft < context.lotHealth().minHealthyPieces();
    List<UUID> remnantPieces =
        remnant
            ? remainingPool.stream().map(WholePieceSearch.Piece::id).sorted().toList()
            : List.of();
    BigDecimal remnantCanonical =
        remnantPieces.stream()
            .map(id -> context.piecesById().get(id).canonicalMeasure())
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
        remnantPieces,
        remnant ? context.toLineUnit(remnantCanonical) : null,
        closes);
  }
}
