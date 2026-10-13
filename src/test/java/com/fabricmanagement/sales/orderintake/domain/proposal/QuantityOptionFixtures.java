package com.fabricmanagement.sales.orderintake.domain.proposal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Builds proposal records for tests of the acceptance and confirmation flows, in the shape those
 * tests describe: one free, applicable option per call (STOCK-PROPOSAL-2 added roles, reasons, held
 * stock and the apply capability; these flows do not depend on them).
 */
public final class QuantityOptionFixtures {

  private QuantityOptionFixtures() {}

  public static QuantityOption option(
      String optionKey,
      QuantityOption.OptionKind kind,
      QuantityOption.Compatibility compatibility,
      BigDecimal quantity,
      BigDecimal canonicalQuantity,
      BigDecimal difference,
      BigDecimal differencePercent,
      boolean outsideAgreedTolerance,
      List<QuantityOption.LotPart> lots) {
    return new QuantityOption(
        optionKey,
        QuantityOption.OptionRole.MAIN,
        false,
        kind,
        compatibility,
        QuantityOption.StockSource.FREE,
        quantity,
        canonicalQuantity,
        difference,
        differencePercent,
        outsideAgreedTolerance
            ? QuantityOption.ToleranceStatus.OUTSIDE
            : QuantityOption.ToleranceStatus.WITHIN,
        List.of(),
        lots,
        List.of(),
        compatibility == QuantityOption.Compatibility.PENDING_CONFIRMATION
            ? QuantityOption.BlockedReason.COMPATIBILITY_CONFIRMATION_REQUIRED
            : null);
  }

  public static QuantityOption.LotPart lot(
      UUID batchId,
      String lotNo,
      List<UUID> pieceIds,
      BigDecimal quantity,
      int remainingEligiblePieces,
      int unknownPieces,
      boolean singleRemnant,
      UUID remnantPieceId,
      BigDecimal remnantQuantity) {
    return new QuantityOption.LotPart(
        batchId,
        lotNo,
        null,
        pieceIds,
        quantity,
        remainingEligiblePieces,
        remnantQuantity == null ? BigDecimal.ZERO : remnantQuantity,
        unknownPieces,
        singleRemnant,
        remnantPieceId,
        remnantQuantity,
        remainingEligiblePieces == 0 && unknownPieces == 0);
  }

  /** The old result shape; the withheld-outside-tolerance count is gone and ignored. */
  public static QuantityEvaluationResult result(
      QuantityEvaluationResult.EvaluationStatus status,
      BigDecimal requestedQuantity,
      String unit,
      String canonicalUnit,
      List<QuantityOption> options,
      int eligiblePieces,
      int unknownPieces,
      int excludedPieces,
      List<String> unknownReasons,
      boolean agedByProductionDate,
      int ignoredWithheldCount) {
    return new QuantityEvaluationResult(
        status,
        requestedQuantity,
        unit,
        canonicalUnit,
        options,
        options.isEmpty() ? null : 0,
        true,
        eligiblePieces,
        unknownPieces,
        excludedPieces,
        unknownReasons,
        agedByProductionDate);
  }
}
