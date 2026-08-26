package com.fabricmanagement.production.core.workorder.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkOrderConsumptionSummaryResponse(
    UUID workOrderId,
    BigDecimal plannedQty,
    BigDecimal totalConsumedWeight,
    String unit,
    List<ProductBreakdown> productBreakdown) {

  @Schema(name = "ConsumptionSummaryProductBreakdown")
  public record ProductBreakdown(
      ProductType productType, BigDecimal consumedWeight, long consumptionCount) {}
}
