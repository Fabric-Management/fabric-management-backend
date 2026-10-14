package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.Holder;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.LotIntents;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What each lot offers to a tier of stock (STOCK-PROPOSAL-2): free stock first, held stock only as
 * an option to weigh.
 */
final class StockPools {

  private StockPools() {}

  /** Free stock is offered; held stock, which others hold, only weighed. */
  enum Tier {
    FREE,
    HELD
  }

  /**
   * What the lot's unheld pieces may give free: unheld eligible stock less the part of quote
   * intents not already pinned to held pieces. Null when it cannot be stated.
   */
  record LotFree(
      BigDecimal canonical, boolean unconvertibleIntent, boolean unverifiedHold, boolean intents) {
    boolean known() {
      return canonical != null;
    }
  }

  /**
   * The pieces each lot offers to a tier. Free: eligible pieces without a hold, bounded by the
   * lot's free quantity; a lot whose free quantity cannot be stated offers nothing free. Held:
   * every eligible piece.
   */
  static Map<UUID, WholePieceSearch.Lot> pools(EvaluationContext context, Tier tier) {
    Map<UUID, WholePieceSearch.Lot> pools = new LinkedHashMap<>();
    for (ProposalLot lot : context.lots()) {
      LotFree lotFree = context.free().get(lot.batchId());
      List<WholePieceSearch.Piece> pieces;
      long capacity = WholePieceSearch.Lot.UNLIMITED;
      if (tier == Tier.FREE) {
        if (!lotFree.known()) {
          pieces = List.of();
        } else {
          pieces = poolPieces(context, lot, true);
          capacity = QuantityEvaluator.floorUnits(lotFree.canonical());
        }
      } else {
        pieces = poolPieces(context, lot, false);
      }
      pools.put(lot.batchId(), new WholePieceSearch.Lot(lot.batchId(), pieces, capacity));
    }
    return pools;
  }

  private static List<WholePieceSearch.Piece> poolPieces(
      EvaluationContext context, ProposalLot lot, boolean freeOnly) {
    return lot.pieces().stream()
        .filter(piece -> piece.state() == PieceState.ELIGIBLE)
        .filter(
            piece ->
                !freeOnly || !context.input().holds().pieceHolds().containsKey(piece.stockUnitId()))
        .map(
            piece ->
                new WholePieceSearch.Piece(
                    piece.stockUnitId(), QuantityEvaluator.toUnits(piece.canonicalMeasure())))
        .filter(piece -> piece.units() > 0)
        .toList();
  }

  static LotFree lotFree(QuantityEvaluator.Input input, ProposalLot lot) {
    LotIntents intents = input.holds().lotIntents().get(lot.batchId());
    boolean hasIntents = intents != null && !intents.intents().isEmpty();
    boolean unconvertible =
        hasIntents && intents.intents().stream().anyMatch(intent -> intent.canonical() == null);
    List<ProposalPiece> eligible =
        lot.pieces().stream().filter(piece -> piece.state() == PieceState.ELIGIBLE).toList();
    boolean unverified =
        eligible.stream()
            .map(piece -> input.holds().pieceHolds().get(piece.stockUnitId()))
            .anyMatch(holder -> holder != null && !holder.verified());
    if (unconvertible || unverified) {
      return new LotFree(null, unconvertible, unverified, hasIntents);
    }
    BigDecimal unheld = BigDecimal.ZERO;
    Map<UUID, BigDecimal> pinned = new LinkedHashMap<>();
    for (ProposalPiece piece : eligible) {
      Holder holder = input.holds().pieceHolds().get(piece.stockUnitId());
      if (holder == null) {
        unheld = unheld.add(piece.canonicalMeasure());
      } else {
        pinned.merge(holder.quoteLineId(), piece.canonicalMeasure(), BigDecimal::add);
      }
    }
    Map<UUID, BigDecimal> intended = new LinkedHashMap<>();
    if (hasIntents) {
      intents
          .intents()
          .forEach(
              intent ->
                  intended.merge(
                      intent.holder().quoteLineId(), intent.canonical(), BigDecimal::add));
    }
    BigDecimal uncovered = BigDecimal.ZERO;
    for (Map.Entry<UUID, BigDecimal> line : intended.entrySet()) {
      BigDecimal open =
          line.getValue().subtract(pinned.getOrDefault(line.getKey(), BigDecimal.ZERO));
      uncovered = uncovered.add(open.max(BigDecimal.ZERO));
    }
    return new LotFree(unheld.subtract(uncovered).max(BigDecimal.ZERO), false, false, hasIntents);
  }
}
