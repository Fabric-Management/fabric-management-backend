package com.fabricmanagement.production.quality.decision.domain;

import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import java.util.Set;

/** Shared unit-status policy used by quality decision reads and writes. */
public final class QualityDecisionEligibility {

  private static final Set<StockUnitStatus> UNIT_STATUS_ELIGIBLE =
      Set.of(
          StockUnitStatus.AVAILABLE,
          StockUnitStatus.PARTIAL,
          StockUnitStatus.QUARANTINE,
          StockUnitStatus.ON_HOLD);

  private QualityDecisionEligibility() {}

  public static Set<StockUnitStatus> unitStatusEligibleStatuses() {
    return UNIT_STATUS_ELIGIBLE;
  }

  public static boolean isUnitStatusEligible(StockUnitStatus status) {
    return UNIT_STATUS_ELIGIBLE.contains(status);
  }
}
