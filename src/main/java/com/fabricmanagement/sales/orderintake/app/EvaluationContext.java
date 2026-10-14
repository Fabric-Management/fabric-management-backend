package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.ToleranceStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The facts one evaluation works from (STOCK-PROPOSAL-2): the input, the lots in age order, every
 * piece and lot by id, the agreed tolerance as a window of search units, the lots' age ranks and
 * what each lot gives free. Built once; read by the search, the option builder, the held-stock view
 * and the reasons.
 */
record EvaluationContext(
    QuantityEvaluator.Input input,
    List<ProposalLot> lots,
    LotHealthPolicy lotHealth,
    boolean aged,
    long target,
    long windowLow,
    long windowHigh,
    Map<UUID, ProposalLot> lotsById,
    Map<UUID, ProposalPiece> piecesById,
    Map<UUID, UUID> lotOfPiece,
    Map<UUID, StockPools.LotFree> free,
    Map<UUID, Integer> ageRanks) {

  static final BigDecimal HUNDRED = new BigDecimal("100");

  static EvaluationContext of(
      QuantityEvaluator.Input input,
      List<ProposalLot> lots,
      LotHealthPolicy lotHealth,
      boolean aged) {
    long target = QuantityEvaluator.toUnits(input.canonicalTarget());
    Map<UUID, ProposalLot> lotsById = new LinkedHashMap<>();
    Map<UUID, ProposalPiece> piecesById = new LinkedHashMap<>();
    Map<UUID, UUID> lotOfPiece = new LinkedHashMap<>();
    Map<UUID, StockPools.LotFree> free = new LinkedHashMap<>();
    for (ProposalLot lot : lots) {
      lotsById.put(lot.batchId(), lot);
      for (ProposalPiece piece : lot.pieces()) {
        piecesById.put(piece.stockUnitId(), piece);
        lotOfPiece.put(piece.stockUnitId(), lot.batchId());
      }
      free.put(lot.batchId(), StockPools.lotFree(input, lot));
    }
    // The agreed tolerance as a window of search units around the target.
    BigDecimal units = BigDecimal.valueOf(target);
    long windowHigh =
        target
            + units
                .multiply(input.agreedToleranceUpPct())
                .divide(HUNDRED, 0, RoundingMode.FLOOR)
                .longValueExact();
    long windowLow =
        Math.max(
            1,
            target
                - units
                    .multiply(input.agreedToleranceDownPct())
                    .divide(HUNDRED, 0, RoundingMode.FLOOR)
                    .longValueExact());
    List<Instant> dates =
        lots.stream()
            .map(ProposalLot::productionDate)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
    Map<UUID, Integer> ageRanks = new LinkedHashMap<>();
    for (ProposalLot lot : lots) {
      ageRanks.put(
          lot.batchId(),
          lot.productionDate() == null ? Integer.MAX_VALUE : dates.indexOf(lot.productionDate()));
    }
    return new EvaluationContext(
        input,
        lots,
        lotHealth,
        aged,
        target,
        windowLow,
        windowHigh,
        lotsById,
        piecesById,
        lotOfPiece,
        free,
        ageRanks);
  }

  /** A canonical quantity in the line unit. */
  BigDecimal toLineUnit(BigDecimal canonical) {
    return input.toLineUnit().apply(canonical);
  }

  /** Against the agreed tolerance; 0% on a side accepts no deviation on that side. */
  ToleranceStatus tolerance(BigDecimal difference) {
    if (difference.signum() == 0) {
      return ToleranceStatus.WITHIN;
    }
    BigDecimal limit =
        difference.signum() > 0 ? input.agreedToleranceUpPct() : input.agreedToleranceDownPct();
    boolean outside =
        difference.abs().multiply(HUNDRED).compareTo(input.requestedQuantity().multiply(limit)) > 0;
    return outside ? ToleranceStatus.OUTSIDE : ToleranceStatus.WITHIN;
  }

  /** The search units of picked pieces. */
  long units(List<UUID> pieceIds) {
    return pieceIds.stream()
        .mapToLong(id -> QuantityEvaluator.toUnits(piecesById.get(id).canonicalMeasure()))
        .sum();
  }
}
