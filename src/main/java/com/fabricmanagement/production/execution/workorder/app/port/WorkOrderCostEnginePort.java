package com.fabricmanagement.production.execution.workorder.app.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WorkOrderCostEnginePort {

  /**
   * Sprint 5: Single-material actual cost — kept for potential future use.
   *
   * @deprecated Prefer {@link #computeActualCostFromConsumptions} for accurate blending cost.
   */
  @Deprecated(since = "Sprint 6")
  ComputedCostSnapshot computeActualCost(
      UUID tenantId,
      UUID workOrderId,
      String moduleType,
      UUID materialId,
      BigDecimal actualQuantityKg,
      UUID tradingPartnerId);

  /**
   * Sprint 6: Multi-material actual cost.
   *
   * <p>Raw-material lines are computed per consumption record ({@code consumedWeight ×
   * materialUnitPrice}). Non-raw-material lines (LABOR, OVERHEAD, etc.) are still derived from the
   * output batch using the existing template mechanism.
   *
   * @param outputModuleType materialType of the output batch (e.g. "YARN") — used for template
   * @param outputMaterialId materialId of the output batch — used for non-raw-material prices
   * @param actualOutputQty net output quantity from WorkOrderCompletedEvent
   * @param consumptions one entry per WorkOrderConsumption record
   */
  ComputedCostSnapshot computeActualCostFromConsumptions(
      UUID tenantId,
      UUID workOrderId,
      String outputModuleType,
      UUID outputMaterialId,
      BigDecimal actualOutputQty,
      UUID tradingPartnerId,
      List<ConsumptionCostInput> consumptions);
}
