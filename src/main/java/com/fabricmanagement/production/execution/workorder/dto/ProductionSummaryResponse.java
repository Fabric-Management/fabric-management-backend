package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductionSummaryResponse(
    UUID workOrderId,
    BigDecimal plannedQty,
    BigDecimal totalOutputWeight,
    BigDecimal totalConsumedWeight,
    BigDecimal yieldPercentage,
    String unit,
    List<ProductBreakdown> productBreakdown,
    long outputCount) {

  public record ProductBreakdown(
      ProductType productType, BigDecimal outputWeight, long outputCount) {}
}
