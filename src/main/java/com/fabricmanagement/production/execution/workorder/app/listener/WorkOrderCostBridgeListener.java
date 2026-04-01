package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.workorder.app.port.ComputedCostSnapshot;
import com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderCompletedEvent;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleWorkOrderCompletedEvent(WorkOrderCompletedEvent event) {
    log.info(
        "Received WorkOrderCompletedEvent for WorkOrder: {}. Initiating actual cost calculation.",
        event.getWorkOrderId());

    try {
      // 1. Fetch WorkOrder
      Optional<WorkOrder> workOrderOpt = workOrderRepository.findById(event.getWorkOrderId());
      if (workOrderOpt.isEmpty() || !workOrderOpt.get().getTenantId().equals(event.getTenantId())) {
        log.warn(
            "WorkOrder {} not found or tenant mismatch. Cost calculation skipped.",
            event.getWorkOrderId());
        return;
      }
      WorkOrder workOrder = workOrderOpt.get();

      // 2. Find output Batch to determine material and module type
      Optional<Batch> batchOpt =
          batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
              event.getTenantId(), event.getWorkOrderId(), BatchSourceType.INTERNAL_PRODUCTION);

      if (batchOpt.isEmpty()) {
        log.warn(
            "No output batch found for WorkOrder: {}. Cost calculation skipped.",
            event.getWorkOrderId());
        return;
      }
      Batch batch = batchOpt.get();

      // 3. Compute cost via Port (adapter handles cross-module logic)
      ComputedCostSnapshot snapshot =
          costEnginePort.computeActualCost(
              event.getTenantId(),
              event.getWorkOrderId(),
              batch.getMaterialType().name(),
              batch.getMaterialId(),
              event.getActualQty(),
              workOrder.getTradingPartnerId());

      // 4. Update WorkOrder actual cost
      workOrder.updateActualCost(snapshot.totalActualCost(), snapshot.currency());
      workOrderRepository.save(workOrder);

      log.info(
          "Actual cost computed successfully for WorkOrder: {}. Cost: {} {}",
          event.getWorkOrderId(),
          snapshot.totalActualCost(),
          snapshot.currency());

    } catch (Exception e) {
      log.error("Failed to compute actual cost for WorkOrder: {}", event.getWorkOrderId(), e);
      // We do not rethrow since this is async/eventual consistency and should not break app flow
    }
  }
}
