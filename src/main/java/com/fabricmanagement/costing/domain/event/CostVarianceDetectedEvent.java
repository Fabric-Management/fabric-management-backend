package com.fabricmanagement.costing.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Published when an ACTUAL (or PLANNED) cost deviates significantly from the previous stage.
 *
 * <p>Subscribers: FlowBoard (creates a COSTING task), NotificationHub (HIGH priority alert).
 *
 * <p>Variance ratio = (currentTotal − previousTotal) / previousTotal. Positive = cost overrun;
 * Negative = cost saving.
 */
@Getter
public class CostVarianceDetectedEvent extends DomainEvent {

  private final UUID costCalculationId;
  private final CostEntityType entityType;
  private final UUID entityId;
  private final CostStage currentStage;
  private final CostStage previousStage;
  private final BigDecimal previousTotal;
  private final BigDecimal currentTotal;

  /** Fractional variance, e.g. 0.15 = 15 % over budget. */
  private final BigDecimal varianceRatio;

  private final String currency;

  @Builder
  public CostVarianceDetectedEvent(
      UUID tenantId,
      UUID costCalculationId,
      CostEntityType entityType,
      UUID entityId,
      CostStage currentStage,
      CostStage previousStage,
      BigDecimal previousTotal,
      BigDecimal currentTotal,
      BigDecimal varianceRatio,
      String currency) {
    super(tenantId, "COST_VARIANCE_DETECTED");
    this.costCalculationId = costCalculationId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.currentStage = currentStage;
    this.previousStage = previousStage;
    this.previousTotal = previousTotal;
    this.currentTotal = currentTotal;
    this.varianceRatio = varianceRatio;
    this.currency = currency;
  }
}
