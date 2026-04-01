package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.workorder.app.port.ComputedCostSnapshot;
import com.fabricmanagement.production.execution.workorder.app.port.ConsumptionCostInput;
import com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderConsumption;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderCompletedEvent;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderConsumptionRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderCostBridgeListener {

  private final WorkOrderCostEnginePort costEnginePort;
  private final BatchRepository batchRepository;
  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderConsumptionRepository workOrderConsumptionRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleWorkOrderCompletedEvent(WorkOrderCompletedEvent event) {
    log.info(
        "WorkOrderCompletedEvent received for WorkOrder: {}. Initiating multi-material cost calc.",
        event.getWorkOrderId());

    try {
      // 1. Load WorkOrder — tenant guard
      Optional<WorkOrder> workOrderOpt = workOrderRepository.findById(event.getWorkOrderId());
      if (workOrderOpt.isEmpty() || !workOrderOpt.get().getTenantId().equals(event.getTenantId())) {
        log.warn(
            "WorkOrder {} not found or tenant mismatch. Cost skipped.", event.getWorkOrderId());
        return;
      }
      WorkOrder workOrder = workOrderOpt.get();

      // 2. Find the output batch (provides outputModuleType and outputMaterialId)
      Optional<Batch> batchOpt =
          batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
              event.getTenantId(), event.getWorkOrderId(), BatchSourceType.INTERNAL_PRODUCTION);
      if (batchOpt.isEmpty()) {
        log.warn("No output batch for WorkOrder: {}. Cost skipped.", event.getWorkOrderId());
        return;
      }
      Batch outputBatch = batchOpt.get();

      // 3. Load all active consumption records
      List<WorkOrderConsumption> consumptions =
          workOrderConsumptionRepository
              .findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
                  event.getTenantId(), event.getWorkOrderId());

      if (consumptions.isEmpty()) {
        log.warn("No consumption records for WorkOrder {}. Cost skipped.", event.getWorkOrderId());
        return;
      }

      // 4. Map to port DTOs — filter nulls (legacy records pre-Sprint 6 may lack materialId)
      List<ConsumptionCostInput> costInputs =
          consumptions.stream()
              .filter(c -> c.getMaterialId() != null)
              .map(
                  c ->
                      new ConsumptionCostInput(
                          c.getMaterialId(),
                          c.getMaterialType().name(), // e.g. "FIBER", "YARN"
                          c.getConsumedWeight(),
                          c.getUnit()))
              .toList();

      if (costInputs.isEmpty()) {
        log.warn(
            "All {} consumption records for WorkOrder {} lack materialId (pre-Sprint 6 data). "
                + "Cost skipped.",
            consumptions.size(),
            event.getWorkOrderId());
        return;
      }

      // 5. Compute cost via Port (cross-module boundary)
      ComputedCostSnapshot snapshot =
          costEnginePort.computeActualCostFromConsumptions(
              event.getTenantId(),
              event.getWorkOrderId(),
              outputBatch.getMaterialType().name(),
              outputBatch.getMaterialId(),
              event.getActualQty(),
              workOrder.getTradingPartnerId(),
              costInputs);

      // 6. Write back to WorkOrder
      workOrder.updateActualCost(snapshot.totalActualCost(), snapshot.currency());
      workOrderRepository.save(workOrder);

      log.info(
          "Multi-material cost computed for WorkOrder {}. Total: {} {} ({} input lines)",
          event.getWorkOrderId(),
          snapshot.totalActualCost(),
          snapshot.currency(),
          costInputs.size());

    } catch (Exception e) {
      log.error(
          "Failed to compute actual cost for WorkOrder: {}. WorkOrder status is COMPLETED — "
              + "cost can be recalculated manually.",
          event.getWorkOrderId(),
          e);
    }
  }
}
