package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles planned cost (re)calculation for approved WorkOrders.
 *
 * <p>Shared logic used by both:
 *
 * <ul>
 *   <li>{@link
 *       com.fabricmanagement.production.execution.workorder.app.listener.WorkOrderPlannedCostListener}
 *       — automatically on WorkOrder approval
 *   <li>Manual recalculation endpoint — when automatic calculation failed (e.g. missing price list
 *       or template)
 * </ul>
 *
 * <p>Idempotent: calling multiple times is safe. {@code CostCalculationService.computePlanned()}
 * soft-deletes the previous PLANNED calculation before writing the new one, and the write-back to
 * {@code WorkOrder.plannedCost} is an overwrite.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkOrderPlannedCostTriggerService {

  private final WorkOrderCostEnginePort costEnginePort;
  private final WorkOrderRepository workOrderRepository;

  /**
   * Triggers planned cost calculation for a WorkOrder.
   *
   * <p>Preconditions (throws {@link WorkOrderDomainException} if violated):
   *
   * <ol>
   *   <li>WorkOrder must exist and belong to the current tenant
   *   <li>WorkOrder must be in APPROVED or later status (not DRAFT/PENDING_APPROVAL)
   *   <li>WorkOrder must have {@code outputMaterialId} and {@code moduleType} set
   * </ol>
   *
   * @param workOrderId the WorkOrder to compute planned cost for
   * @return updated WorkOrderResponse with plannedCost and plannedCostCurrency
   */
  @Transactional
  public WorkOrderResponse triggerPlannedCost(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();

    // 1. Load + guard
    WorkOrder workOrder =
        workOrderRepository
            .findById(workOrderId)
            .filter(wo -> wo.getTenantId().equals(tenantId))
            .filter(BaseEntity::getIsActive)
            .orElseThrow(() -> new WorkOrderDomainException("WorkOrder not found: " + workOrderId));

    if (workOrder.getStatus() == WorkOrderStatus.DRAFT
        || workOrder.getStatus() == WorkOrderStatus.PENDING_APPROVAL) {
      throw new WorkOrderDomainException(
          "Planned cost requires at least APPROVED status. Current: " + workOrder.getStatus());
    }

    // 2. Guard: outputMaterialId + moduleType must be present (Sprint 12 enrichment)
    if (workOrder.getOutputMaterialId() == null) {
      throw new WorkOrderDomainException(
          "WorkOrder "
              + workOrderId
              + " missing outputMaterialId — cannot compute planned cost. "
              + "This may be a pre-Sprint 12 WorkOrder without enrichment data.");
    }
    if (workOrder.getModuleType() == null) {
      throw new WorkOrderDomainException(
          "WorkOrder " + workOrderId + " missing moduleType — cannot compute planned cost.");
    }

    // 3. Compute via Port → CostCalculationService.computePlanned() → write-back via
    // WorkOrderPlanningUpdatePort
    costEnginePort.computePlannedCost(
        tenantId,
        workOrderId,
        workOrder.getModuleType(),
        workOrder.getOutputMaterialId(),
        workOrder.getPlannedQty(),
        workOrder.getTradingPartnerId());

    // 4. Reload to pick up write-back changes from WorkOrderPlanningUpdatePort
    WorkOrder updated =
        workOrderRepository
            .findById(workOrderId)
            .orElseThrow(
                () ->
                    new WorkOrderDomainException(
                        "WorkOrder not found after update: " + workOrderId));

    log.info(
        "Planned cost triggered for WorkOrder {}. plannedCost={} {}",
        workOrderId,
        updated.getPlannedCost(),
        updated.getPlannedCostCurrency());

    return WorkOrderResponse.from(updated);
  }
}
