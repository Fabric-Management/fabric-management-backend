package com.fabricmanagement.production.core.stockunit.domain;

/** Commercial planning and physical execution remain separate facts. */
public enum StockUnitCutPlanState {
  PREPARED,
  APPROVED,
  RECORDED,
  DISPUTED,
  COMPLETED,
  CANCELLED;

  public boolean beforeCut() {
    return this == PREPARED || this == APPROVED;
  }
}
