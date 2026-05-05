package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.workorder.app.port.ComputedCostSnapshot;
import com.fabricmanagement.production.execution.workorder.app.port.ConsumptionCostInput;
import com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderConsumption;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderConsumptionRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles actual cost (re)calculation for completed WorkOrders.
 *
 * <p>Shared logic used by both:
 *
 * <ul>
 *   <li>{@link
 *       com.fabricmanagement.production.execution.workorder.app.listener.WorkOrderCostBridgeListener}
 *       — automatically on WorkOrder completion
 *   <li>Manual recalculation endpoint — when automatic calculation failed (e.g. missing price list)
 * </ul>
 *
 * <p>Idempotent: calling multiple times is safe. CostCalculationService soft-deletes the previous
 * ACTUAL calculation before writing the new one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkOrderCostRecalculationService {

  private final WorkOrderCostEnginePort costEnginePort;
  private final BatchRepository batchRepository;
  private final WorkOrderConsumptionRepository workOrderConsumptionRepository;
  private final WorkOrderRepository workOrderRepository;

  /**
   * Recalculates actual cost for a WorkOrder.
   *
   * <p>Preconditions (throws {@link WorkOrderDomainException} if violated):
   *
   * <ol>
   *   <li>WorkOrder must exist and belong to the current tenant
   *   <li>WorkOrder must be in COMPLETED status
   *   <li>At least one consumption record with materialId must exist
   *   <li>An output batch must exist (created at startProduction)
   * </ol>
   *
   * @param workOrderId the WorkOrder to recalculate cost for
   * @return updated WorkOrderResponse with new actualCost and actualCostCurrency
   */
  @Transactional
  public WorkOrderResponse recalculateActualCost(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();

    // 1. Load + guard
    WorkOrder workOrder =
        workOrderRepository
            .findById(workOrderId)
            .filter(wo -> wo.getTenantId().equals(tenantId))
            .filter(BaseEntity::getIsActive)
            .orElseThrow(() -> new WorkOrderDomainException("WorkOrder not found: " + workOrderId));

    if (workOrder.getStatus() != WorkOrderStatus.COMPLETED) {
      throw new WorkOrderDomainException(
          "Cost recalculation requires COMPLETED status. Current: " + workOrder.getStatus());
    }

    // 2. Output batch (provides moduleType + materialId for non-raw-material lines)
    Batch outputBatch =
        batchRepository
            .findFirstByTenantIdAndSourceIdAndSourceType(
                tenantId, workOrderId, BatchSourceType.INTERNAL_PRODUCTION)
            .orElseThrow(
                () ->
                    new WorkOrderDomainException(
                        "No output batch found for WorkOrder: " + workOrderId));

    // 3. Consumptions
    List<WorkOrderConsumption> consumptions =
        workOrderConsumptionRepository
            .findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(tenantId, workOrderId);

    if (consumptions.isEmpty()) {
      throw new WorkOrderDomainException(
          "No consumption records found for WorkOrder: " + workOrderId);
    }

    // 4. Map to port DTOs — Sprint 6+ records have materialId; older records may not
    List<ConsumptionCostInput> costInputs =
        consumptions.stream()
            .filter(c -> c.getMaterialId() != null)
            .map(
                c ->
                    new ConsumptionCostInput(
                        c.getMaterialId(),
                        workOrder
                            .getModuleType(), // Production spec says parent consumes same as WO
                        c.getConsumedWeight(),
                        c.getUnit()))
            .toList();

    if (costInputs.isEmpty()) {
      throw new WorkOrderDomainException(
          "No consumption records with materialId found for WorkOrder: "
              + workOrderId
              + ". Records created before Sprint 6 migration lack materialId.");
    }

    // 5. Compute via Port
    ComputedCostSnapshot snapshot =
        costEnginePort.computeActualCostFromConsumptions(
            tenantId,
            workOrderId,
            workOrder.getModuleType(),
            outputBatch.getMaterialId(),
            workOrder.getActualQty(),
            workOrder.getTradingPartnerId(),
            costInputs);

    // 6. Persist
    workOrder.updateActualCost(snapshot.totalActualCost(), snapshot.currency());
    workOrderRepository.save(workOrder);

    log.info(
        "Cost recalculated for WorkOrder {}. actualCost={} {}",
        workOrderId,
        snapshot.totalActualCost(),
        snapshot.currency());

    return WorkOrderResponse.from(workOrder);
  }
}
