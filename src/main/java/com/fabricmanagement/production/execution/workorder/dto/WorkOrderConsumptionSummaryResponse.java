package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkOrderConsumptionSummaryResponse(
    UUID workOrderId,
    BigDecimal plannedQty,
    BigDecimal totalConsumedWeight,
    String unit,
    List<MaterialBreakdown> materialBreakdown) {

  public record MaterialBreakdown(
      MaterialType materialType, BigDecimal consumedWeight, long consumptionCount) {}
}
