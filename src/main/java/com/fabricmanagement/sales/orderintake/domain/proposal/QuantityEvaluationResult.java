package com.fabricmanagement.sales.orderintake.domain.proposal;

import java.math.BigDecimal;
import java.util.List;

/**
 * The stored outcome of a quantity evaluation (SOI D2). Unknown evidence is reported by reason and
 * count; it never becomes "no stock" or "suitable".
 */
public record QuantityEvaluationResult(
    EvaluationStatus status,
    BigDecimal requestedQuantity,
    String unit,
    String canonicalUnit,
    List<QuantityOption> options,
    int eligiblePieces,
    int unknownPieces,
    int excludedPieces,
    List<String> unknownReasons,
    boolean agedByProductionDate,
    int optionsOutsideAgreedTolerance) {

  public QuantityEvaluationResult {
    options = options == null ? List.of() : List.copyOf(options);
    unknownReasons = unknownReasons == null ? List.of() : List.copyOf(unknownReasons);
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "QuantityEvaluationStatus", enumAsRef = true)
  public enum EvaluationStatus {
    /** The requested quantity is available from whole pieces without a single remnant. */
    EXACT,
    OPTIONS,
    NO_ELIGIBLE_STOCK,
    UNKNOWN
  }
}
