package com.fabricmanagement.costing.api.dto;

import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read-only response for a CostCalculation. */
public record CostCalculationResponse(
    UUID id,
    CostEntityType entityType,
    UUID entityId,
    String moduleType,
    CostStage stage,
    BigDecimal totalCost,
    String currency,
    Instant calculatedAt) {

  public static CostCalculationResponse from(CostCalculation calc) {
    return new CostCalculationResponse(
        calc.getId(),
        calc.getEntityType(),
        calc.getEntityId(),
        calc.getModuleType(),
        calc.getStage(),
        calc.getTotalCost(),
        calc.getCurrency(),
        calc.getCalculatedAt());
  }
}
