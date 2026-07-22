package com.fabricmanagement.production.quality.decision.domain;

public enum QualityDecisionBlockedReason {
  BATCH_RESERVED,
  BATCH_CONSUMED,
  BATCH_STATUS_BLOCKED,
  NO_ELIGIBLE_UNITS,
  INELIGIBLE_ACTIVE_UNITS
}
