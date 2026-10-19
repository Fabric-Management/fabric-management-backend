package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.Holder;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.Intent;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.LotIntents;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.HeldStock;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.HeldStockKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What other quotes hold that an option would take (STOCK-PROPOSAL-2): the owners it names and
 * whether the hold is verified. Held stock is an option to weigh, never to apply.
 */
final class HeldStockView {

  private HeldStockView() {}

  /** The holds a take touches on one lot. */
  record HeldOnLot(List<HeldStock> entries, boolean verified, boolean unverified) {}

  /**
   * What other quotes hold that taking {@code taken} from the lot touches: held pieces it takes
   * and, when its unheld pieces exceed what the lot gives free or that cannot be stated, the lot's
   * intents and unverifiable hold records.
   */
  static HeldOnLot heldOnLot(EvaluationContext context, ProposalLot lot, Set<UUID> taken) {
    Map<UUID, Holder> pieceHolds = context.input().holds().pieceHolds();
    List<HeldStock> entries = new ArrayList<>();
    boolean verified = false;
    boolean unverified = false;
    Map<UUID, List<UUID>> verifiedPieces = new LinkedHashMap<>();
    Map<UUID, Holder> holders = new LinkedHashMap<>();
    List<UUID> unverifiedPieces = new ArrayList<>();
    for (ProposalPiece piece : lot.pieces()) {
      if (!taken.contains(piece.stockUnitId())) {
        continue;
      }
      Holder holder = pieceHolds.get(piece.stockUnitId());
      if (holder == null) {
        continue;
      }
      if (holder.verified()) {
        verifiedPieces
            .computeIfAbsent(holder.quoteLineId(), ignored -> new ArrayList<>())
            .add(piece.stockUnitId());
        holders.putIfAbsent(holder.quoteLineId(), holder);
        verified = true;
      } else {
        unverifiedPieces.add(piece.stockUnitId());
        unverified = true;
      }
    }
    verifiedPieces.forEach(
        (line, pieces) -> entries.add(pieceHold(lot, holders.get(line), pieces)));
    StockPools.LotFree lotFree = context.free().get(lot.batchId());
    BigDecimal takenUnheld =
        lot.pieces().stream()
            .filter(piece -> taken.contains(piece.stockUnitId()))
            .filter(piece -> !pieceHolds.containsKey(piece.stockUnitId()))
            .map(ProposalPiece::canonicalMeasure)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean beyondFree = !lotFree.known() || takenUnheld.compareTo(lotFree.canonical()) > 0;
    if (beyondFree) {
      LotIntents intents = context.input().holds().lotIntents().get(lot.batchId());
      if (intents != null) {
        for (Intent intent : intents.intents()) {
          Holder holder = intent.holder();
          entries.add(
              new HeldStock(
                  lot.batchId(),
                  lot.lotNo(),
                  HeldStockKind.LOT_INTENT,
                  true,
                  holder.quoteId(),
                  holder.quoteNumber(),
                  holder.marketerName(),
                  holder.expiresAt(),
                  List.of(),
                  intent.quantity(),
                  intent.unit(),
                  holder.ownerType()));
          verified = true;
        }
      }
      if (lotFree.unverifiedHold()) {
        lot.pieces().stream()
            .filter(piece -> piece.state() == PieceState.ELIGIBLE)
            .map(ProposalPiece::stockUnitId)
            .filter(id -> !taken.contains(id))
            .filter(
                id -> {
                  Holder holder = pieceHolds.get(id);
                  return holder != null && !holder.verified();
                })
            .forEach(unverifiedPieces::add);
        unverified = true;
      }
    }
    if (!unverifiedPieces.isEmpty()) {
      entries.add(pieceHold(lot, Holder.unverified(), unverifiedPieces));
    }
    return new HeldOnLot(entries, verified, unverified);
  }

  private static HeldStock pieceHold(ProposalLot lot, Holder holder, List<UUID> pieces) {
    return new HeldStock(
        lot.batchId(),
        lot.lotNo(),
        HeldStockKind.PIECE_HOLD,
        holder.verified(),
        holder.verified() ? holder.quoteId() : null,
        holder.verified() ? holder.quoteNumber() : null,
        holder.verified() ? holder.marketerName() : null,
        holder.verified() ? holder.expiresAt() : null,
        pieces,
        null,
        null,
        holder.ownerType());
  }
}
