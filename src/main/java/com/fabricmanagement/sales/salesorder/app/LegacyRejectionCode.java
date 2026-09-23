package com.fabricmanagement.sales.salesorder.app;

/** Preserves the pre-planner sales-domain rejection strings consumed by the transition adapter. */
public final class LegacyRejectionCode {
  private LegacyRejectionCode() {}

  public static String of(OrderCoverLinePlanner.LineAssessment assessment) {
    if (assessment.selectable() || assessment.blockCode() == null) {
      throw new IllegalArgumentException("A blocked line assessment is required");
    }
    return assessment.blockCode() == OrderCoverLinePlanner.BlockCode.REQUIREMENT_INCOMPLETE
        ? String.join(",", assessment.incompleteReasons())
        : assessment.blockCode().name();
  }
}
