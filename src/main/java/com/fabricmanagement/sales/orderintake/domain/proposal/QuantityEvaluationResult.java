package com.fabricmanagement.sales.orderintake.domain.proposal;

import java.math.BigDecimal;
import java.util.List;

/**
 * The stored outcome of a quantity evaluation (SOI D2, STOCK-PROPOSAL-2). Unknown evidence is
 * reported by reason and count; it never becomes "no stock" or "suitable".
 *
 * <p>Options are in the decided card order (Fatih, 2026-10-14): the salesperson's changed pieces,
 * the main proposal, above outside the tolerance, below, pending combinations, held stock; on the
 * same place ascending quantity, then the tenant-first ranking and a deterministic tie-break.
 * {@code mainOptionIndex} points at the main proposal: first, or second after changed pieces; null
 * when no option can be the main one. {@code searchComplete} is false when a search limit was
 * reached: the options found are then not claimed to be the nearest or the best.
 */
public record QuantityEvaluationResult(
    EvaluationStatus status,
    BigDecimal requestedQuantity,
    String unit,
    String canonicalUnit,
    List<QuantityOption> options,
    Integer mainOptionIndex,
    boolean searchComplete,
    int eligiblePieces,
    int unknownPieces,
    int excludedPieces,
    List<String> unknownReasons,
    boolean agedByProductionDate) {

  public QuantityEvaluationResult {
    options = options == null ? List.of() : List.copyOf(options);
    unknownReasons = unknownReasons == null ? List.of() : List.copyOf(unknownReasons);
    if (mainOptionIndex != null && (mainOptionIndex < 0 || mainOptionIndex >= options.size())) {
      throw new IllegalArgumentException("The main option index is outside the options");
    }
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "QuantityEvaluationStatus", enumAsRef = true)
  public enum EvaluationStatus {
    /** The main proposal is exactly the requested quantity. */
    EXACT,
    OPTIONS,
    NO_ELIGIBLE_STOCK,
    UNKNOWN
  }
}
