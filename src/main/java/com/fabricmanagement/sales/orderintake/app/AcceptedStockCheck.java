package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Re-checks the pieces of an accepted option against current stock (SOI R22, A05). An accepted
 * option stays valid while every one of its pieces is still eligible in the same lot, even if the
 * best proposal would now look different; the customer accepted pieces, not a ranking.
 */
final class AcceptedStockCheck {

  private AcceptedStockCheck() {}

  record Result(
      java.math.BigDecimal canonicalQuantity,
      List<UUID> unavailablePieces,
      QuantityOption.Compatibility compatibility,
      List<PieceAllocationPort.PieceRef> pieceRefs) {

    boolean piecesAvailable() {
      return unavailablePieces.isEmpty();
    }

    boolean compatibilityResolved() {
      return compatibility != QuantityOption.Compatibility.PENDING_CONFIRMATION;
    }
  }

  static Result check(
      QuantityEvaluationService.LineStock stock,
      Collection<UUID> pieceIds,
      Collection<UUID> batchIds) {
    Map<UUID, ProposalPiece> pieces = new HashMap<>();
    Map<UUID, UUID> lotOfPiece = new HashMap<>();
    for (ProposalLot lot : stock.lots()) {
      for (ProposalPiece piece : lot.pieces()) {
        pieces.put(piece.stockUnitId(), piece);
        lotOfPiece.put(piece.stockUnitId(), lot.batchId());
      }
    }
    Set<UUID> lots = new LinkedHashSet<>(batchIds);
    List<UUID> unavailable = new ArrayList<>();
    List<PieceAllocationPort.PieceRef> refs = new ArrayList<>();
    java.math.BigDecimal canonicalQuantity = java.math.BigDecimal.ZERO;
    for (UUID pieceId : pieceIds) {
      ProposalPiece piece = pieces.get(pieceId);
      UUID lot = lotOfPiece.get(pieceId);
      if (piece == null || piece.state() != PieceState.ELIGIBLE || !lots.contains(lot)) {
        unavailable.add(pieceId);
      } else {
        canonicalQuantity = canonicalQuantity.add(piece.canonicalMeasure());
        refs.add(new PieceAllocationPort.PieceRef(pieceId, lot, piece.version()));
      }
    }
    return new Result(
        canonicalQuantity, List.copyOf(unavailable), compatibility(stock, lots), List.copyOf(refs));
  }

  static QuantityOption.Compatibility compatibility(
      QuantityEvaluationService.LineStock stock, Set<UUID> lots) {
    if (lots.size() <= 1) {
      return QuantityOption.Compatibility.SINGLE_LOT;
    }
    if (stock.confirmedGroups().stream().anyMatch(group -> group.containsAll(lots))) {
      return QuantityOption.Compatibility.CONFIRMED;
    }
    if (stock.toneGroups().stream().anyMatch(group -> group.containsAll(lots))) {
      return QuantityOption.Compatibility.TONE_ACCEPTED;
    }
    return QuantityOption.Compatibility.PENDING_CONFIRMATION;
  }
}
