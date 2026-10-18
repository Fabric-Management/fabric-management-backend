package com.fabricmanagement.sales.orderintake.domain.proposal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stored evaluation in business priority order under policyCode. mainOptionIndex identifies the
 * automatic opening, independently of a manual option's coverage proof. searchComplete=false
 * forbids optimality/absence claims. Historical JSON without policy or coverage evidence reads
 * UNKNOWN; it is never reinterpreted as a current evaluation.
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
    boolean agedByProductionDate,
    ProposalPolicyCode policyCode,
    Integer effectiveMinHealthyPieces,
    QuantityCoverage coverage) {

  public QuantityEvaluationResult(
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
    this(
        status,
        requestedQuantity,
        unit,
        canonicalUnit,
        options,
        mainOptionIndex,
        searchComplete,
        eligiblePieces,
        unknownPieces,
        excludedPieces,
        unknownReasons,
        agedByProductionDate,
        ProposalPolicyCode.UNKNOWN,
        null,
        QuantityCoverage.unknown());
  }

  public QuantityEvaluationResult withPolicy(
      ProposalPolicyCode policy, int minimum, QuantityCoverage proof) {
    return new QuantityEvaluationResult(
        status,
        requestedQuantity,
        unit,
        canonicalUnit,
        options,
        mainOptionIndex,
        searchComplete,
        eligiblePieces,
        unknownPieces,
        excludedPieces,
        unknownReasons,
        agedByProductionDate,
        policy,
        minimum,
        proof);
  }

  public QuantityEvaluationResult {
    policyCode = policyCode == null ? ProposalPolicyCode.UNKNOWN : policyCode;
    coverage = coverage == null ? QuantityCoverage.unknown() : coverage;
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
