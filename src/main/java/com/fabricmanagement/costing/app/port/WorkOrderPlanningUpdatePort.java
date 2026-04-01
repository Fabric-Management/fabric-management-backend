package com.fabricmanagement.costing.app.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port for writing planned cost back to a WorkOrder after calculation.
 *
 * <p>Defined in the costing module; implemented by an adapter in the production module. Follows the
 * same Port/Adapter pattern as {@link
 * com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort} but in the
 * reverse direction (costing → production).
 *
 * <p>The adapter is optional ({@code @Autowired(required = false)}) — costing module remains
 * functional when no production module is present (e.g. isolated tests).
 */
public interface WorkOrderPlanningUpdatePort {

  /**
   * Updates {@code plannedCost} and {@code plannedCostCurrency} on a WorkOrder entity.
   *
   * <p>Called synchronously within {@code computePlanned()}'s transaction. Failures are logged and
   * swallowed — the CostCalculation record must not be affected.
   *
   * @param tenantId the current tenant (for security guard)
   * @param workOrderId the target WorkOrder
   * @param plannedCost the computed total planned cost
   * @param currency the cost currency
   */
  void updatePlannedCost(UUID tenantId, UUID workOrderId, BigDecimal plannedCost, String currency);
}
