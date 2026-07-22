package com.fabricmanagement.production.quality.decision.domain;

import java.util.List;

/** Single source of truth for quality-decision outcome and reason compatibility. */
public final class QualityDecisionReasonPolicy {

  private static final List<ManualQualityReasonCode> QUARANTINE_REASONS =
      List.of(
          ManualQualityReasonCode.SUSPECTED_DAMAGE,
          ManualQualityReasonCode.AWAITING_LAB,
          ManualQualityReasonCode.SUPPLIER_DISPUTE,
          ManualQualityReasonCode.SHADE_CHECK,
          ManualQualityReasonCode.OTHER);

  private static final List<ManualQualityReasonCode> NONCONFORMING_REASONS =
      List.of(
          ManualQualityReasonCode.DAMAGE,
          ManualQualityReasonCode.STAIN,
          ManualQualityReasonCode.SHADE_VARIATION,
          ManualQualityReasonCode.SHORT_LENGTH,
          ManualQualityReasonCode.MEASURE_MISMATCH,
          ManualQualityReasonCode.OTHER);

  private QualityDecisionReasonPolicy() {}

  public static List<ManualQualityReasonCode> manualReasons(QualityDecisionOutcome outcome) {
    return switch (outcome) {
      case RELEASED -> List.of();
      case QUARANTINED -> QUARANTINE_REASONS;
      case NONCONFORMING -> NONCONFORMING_REASONS;
    };
  }

  public static boolean manualReasonRequired(QualityDecisionOutcome outcome) {
    return !manualReasons(outcome).isEmpty();
  }

  public static boolean remarksRequired(QualityReasonCode reasonCode) {
    return reasonCode == QualityReasonCode.OTHER;
  }

  public static boolean remarksRequired(ManualQualityReasonCode reasonCode) {
    return reasonCode == ManualQualityReasonCode.OTHER;
  }

  public static boolean reasonAllowed(
      QualityDecisionOrigin origin, QualityDecisionOutcome outcome, QualityReasonCode reasonCode) {
    if (origin == QualityDecisionOrigin.SYSTEM_RELEASE) {
      return outcome == QualityDecisionOutcome.RELEASED
          && reasonCode == QualityReasonCode.SYSTEM_QC_PASSED;
    }
    if (origin == QualityDecisionOrigin.SYSTEM_QC_EVENT) {
      return (outcome == QualityDecisionOutcome.RELEASED
              && reasonCode == QualityReasonCode.SYSTEM_QC_PASSED)
          || (outcome == QualityDecisionOutcome.NONCONFORMING
              && reasonCode == QualityReasonCode.SYSTEM_QC_REJECTED);
    }
    if (origin != QualityDecisionOrigin.MANUAL) {
      return false;
    }
    return manualReasonRequired(outcome)
        ? reasonCode != null
            && manualReasons(outcome).stream()
                .anyMatch(manualReason -> manualReason.toDomainCode() == reasonCode)
        : reasonCode == null;
  }
}
