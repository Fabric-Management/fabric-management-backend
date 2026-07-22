package com.fabricmanagement.production.quality.decision.domain;

public record QualityDecisionCapability(
    boolean allowed, QualityDecisionBlockedReason blockedReason) {

  public QualityDecisionCapability {
    if (allowed == (blockedReason != null)) {
      throw new IllegalArgumentException(
          "Allowed quality-decision capabilities must not have a blocked reason");
    }
  }

  public static QualityDecisionCapability permitted() {
    return new QualityDecisionCapability(true, null);
  }

  public static QualityDecisionCapability blocked(QualityDecisionBlockedReason reason) {
    if (reason == null) {
      throw new IllegalArgumentException("A blocked quality-decision capability requires a reason");
    }
    return new QualityDecisionCapability(false, reason);
  }
}
