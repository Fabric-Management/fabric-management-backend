package com.fabricmanagement.production.execution.workorder.app.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WorkOrderCostEnginePort {

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

  /**
   * Sprint 12: Planned cost computation triggered on WorkOrder approval.
   *
   * <p>Delegates to {@code CostCalculationService.computePlanned()} which also writes the result
   * back to the WorkOrder entity via {@code WorkOrderPlanningUpdatePort} (Sprint 11).
   *
   * @param tenantId owning tenant
   * @param workOrderId the WorkOrder entity ID
   * @param moduleType production module (e.g. "YARN", "WEAVING")
   * @param outputMaterialId the planned output material
   * @param plannedQuantity the work order planned quantity in kg
   * @param tradingPartnerId the selected supplier (for contracted prices)
   * @return snapshot with totalPlannedCost and currency
   */
  ComputedCostSnapshot computePlannedCost(
      UUID tenantId,
      UUID workOrderId,
      String moduleType,
      UUID outputMaterialId,
      BigDecimal plannedQuantity,
      UUID tradingPartnerId);
}
