package com.fabricmanagement.production.quality.decision.domain;

/** Reason codes that a human quality approver may select. */
public enum ManualQualityReasonCode {
  SUSPECTED_DAMAGE,
  AWAITING_LAB,
  SUPPLIER_DISPUTE,
  SHADE_CHECK,
  DAMAGE,
  STAIN,
  SHADE_VARIATION,
  SHORT_LENGTH,
  MEASURE_MISMATCH,
  OTHER;

  public QualityReasonCode toDomainCode() {
    return QualityReasonCode.valueOf(name());
  }
}
