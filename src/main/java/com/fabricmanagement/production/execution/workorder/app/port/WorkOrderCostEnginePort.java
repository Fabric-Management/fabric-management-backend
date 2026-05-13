package com.fabricmanagement.production.execution.workorder.app.port;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WorkOrderCostEnginePort {

  /**
   * Sprint 6: Multi-product actual cost.
   *
   * <p>Raw-product lines are computed per consumption record ({@code consumedWeight ×
   * productUnitPrice}). Non-raw-product lines (LABOR, OVERHEAD, etc.) are still derived from the
   * output batch using the existing template mechanism.
   *
   * @param outputModuleType productType of the output batch (e.g. "YARN") — used for template
   * @param outputProductId productId of the output batch — used for non-raw-product prices
   * @param actualOutputQty net output quantity from WorkOrderCompletedEvent
   * @param consumptions one entry per WorkOrderConsumption record
   */
  ComputedCostSnapshot computeActualCostFromConsumptions(
      UUID tenantId,
      UUID workOrderId,
      WorkOrderModuleType outputModuleType,
      UUID outputProductId,
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
   * @param outputProductId the planned output product
   * @param plannedQuantity the work order planned quantity in kg
   * @param tradingPartnerId the selected supplier (for contracted prices)
   * @return snapshot with totalPlannedCost and currency
   */
  ComputedCostSnapshot computePlannedCost(
      UUID tenantId,
      UUID workOrderId,
      WorkOrderModuleType moduleType,
      UUID outputProductId,
      BigDecimal plannedQuantity,
      UUID tradingPartnerId);
}
