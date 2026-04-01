package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkOrderOutputSummaryResponse(
    UUID workOrderId,
    BigDecimal plannedQty,
    BigDecimal totalOutputWeight,
    BigDecimal totalConsumedWeight,
    BigDecimal yieldPercentage,
    String unit,
    List<MaterialBreakdown> materialBreakdown,
    long outputCount) {

  public record MaterialBreakdown(
      MaterialType materialType, BigDecimal outputWeight, long outputCount) {}
}
