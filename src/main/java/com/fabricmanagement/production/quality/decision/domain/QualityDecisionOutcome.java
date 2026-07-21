package com.fabricmanagement.production.quality.decision.domain;

import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;

public enum QualityDecisionOutcome {
  RELEASED,
  QUARANTINED,
  NONCONFORMING;

  public QualityDisposition toDisposition() {
    return QualityDisposition.valueOf(name());
  }
}
