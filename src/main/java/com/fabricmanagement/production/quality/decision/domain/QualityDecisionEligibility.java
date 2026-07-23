package com.fabricmanagement.production.quality.decision.domain;

import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import java.math.BigDecimal;
import java.util.Set;

/** Shared unit-status policy used by quality decision reads and writes. */
public final class QualityDecisionEligibility {

  private static final Set<StockUnitStatus> UNIT_STATUS_ELIGIBLE =
      Set.of(
          StockUnitStatus.AVAILABLE,
          StockUnitStatus.PARTIAL,
          StockUnitStatus.QUARANTINE,
          StockUnitStatus.ON_HOLD);

  private static final Set<BatchStatus> FULL_LOT_BLOCKED_BATCH_STATUSES =
      Set.of(
          BatchStatus.RESERVED,
          BatchStatus.IN_PROGRESS,
          BatchStatus.ON_HOLD,
          BatchStatus.DEPLETED,
          BatchStatus.RETURNED,
          BatchStatus.DESTROYED);

  private static final Set<BatchStatus> SELECTED_UNITS_BLOCKED_BATCH_STATUSES =
      Set.of(
          BatchStatus.RESERVED, BatchStatus.DEPLETED, BatchStatus.RETURNED, BatchStatus.DESTROYED);

  private QualityDecisionEligibility() {}

  public static Set<StockUnitStatus> unitStatusEligibleStatuses() {
    return UNIT_STATUS_ELIGIBLE;
  }

  public static boolean isUnitStatusEligible(StockUnitStatus status) {
    return UNIT_STATUS_ELIGIBLE.contains(status);
  }

  public static QualityDecisionCapability evaluateBatch(
      QualityDecisionScope scope,
      BatchStatus status,
      BigDecimal reservedQuantity,
      BigDecimal consumedQuantity) {
    if (reservedQuantity.compareTo(BigDecimal.ZERO) > 0) {
      return QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_RESERVED);
    }
    if (scope == QualityDecisionScope.FULL_LOT && consumedQuantity.compareTo(BigDecimal.ZERO) > 0) {
      return QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_CONSUMED);
    }
    Set<BatchStatus> blockedStatuses =
        scope == QualityDecisionScope.FULL_LOT
            ? FULL_LOT_BLOCKED_BATCH_STATUSES
            : SELECTED_UNITS_BLOCKED_BATCH_STATUSES;
    if (blockedStatuses.contains(status)) {
      return QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_STATUS_BLOCKED);
    }
    return QualityDecisionCapability.permitted();
  }

  public static QualityDecisionCapability evaluatePopulation(
      QualityDecisionScope scope, long activeUnitCount, long statusEligibleUnitCount) {
    if (activeUnitCount < 0
        || statusEligibleUnitCount < 0
        || statusEligibleUnitCount > activeUnitCount) {
      throw new IllegalArgumentException("Quality-decision population counts are inconsistent");
    }
    if (statusEligibleUnitCount == 0) {
      return QualityDecisionCapability.blocked(QualityDecisionBlockedReason.NO_ELIGIBLE_UNITS);
    }
    if (scope == QualityDecisionScope.FULL_LOT && statusEligibleUnitCount != activeUnitCount) {
      return QualityDecisionCapability.blocked(
          QualityDecisionBlockedReason.INELIGIBLE_ACTIVE_UNITS);
    }
    return QualityDecisionCapability.permitted();
  }

  public static QualityDecisionCapability combine(
      QualityDecisionCapability batchCapability, QualityDecisionCapability populationCapability) {
    return batchCapability.allowed() ? populationCapability : batchCapability;
  }
}
