package com.fabricmanagement.production.execution.stockunit.app;

import com.fabricmanagement.common.domain.event.production.SalesOrderLineStoredEvent;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderLineStorageCheckService {

  private final WorkOrderRepository workOrderRepository;
  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final DomainEventPublisher eventPublisher;

  /**
   * Checks if all WorkOrders for a SalesOrderLine are COMPLETED and their outputs are stored in the
   * warehouse. If so, publishes a SalesOrderLineStoredEvent. This is executed in a fresh
   * transaction (REQUIRES_NEW) after a StockUnit is created.
   *
   * @param tenantId The tenant ID.
   * @param salesOrderLineId The SalesOrderLine ID.
   * @param triggeredByStockUnitId The StockUnit ID that triggered this check.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void publishLineStoredIfAllOutputsStored(
      UUID tenantId, UUID salesOrderLineId, UUID triggeredByStockUnitId) {

    log.debug(
        "Checking if all outputs for salesOrderLineId={} are stored. Triggered by stockUnitId={}",
        salesOrderLineId,
        triggeredByStockUnitId);

    // 1. Find all active WorkOrders for this salesOrderLineId
    List<WorkOrder> workOrders =
        workOrderRepository.findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, salesOrderLineId);

    if (workOrders.isEmpty()) {
      return; // Should not happen if a stock unit was just created for it, but be safe
    }

    // 2. Check if all WOs are completed and have their outputs stored
    boolean allOutputsStored = true;

    for (WorkOrder wo : workOrders) {
      if (wo.getStatus() != WorkOrderStatus.COMPLETED) {
        log.debug(
            "WorkOrder {} is not COMPLETED (status={}). Line is not fully stored.",
            wo.getId(),
            wo.getStatus());
        allOutputsStored = false;
        break; // Fail fast
      }

      // Check if this COMPLETED WO has at least one active StockUnit via a production batch
      List<Batch> outputBatches =
          batchRepository.findByTenantIdAndSourceIdAndSourceTypeAndIsActiveTrueOrderByCreatedAtAsc(
              tenantId, wo.getId(), BatchSourceType.INTERNAL_PRODUCTION);

      List<UUID> batchIds = outputBatches.stream().map(Batch::getId).toList();

      boolean hasStoredStockUnit =
          !batchIds.isEmpty()
              && stockUnitRepository.existsByTenantIdAndBatchIdInAndIsActiveTrue(
                  tenantId, batchIds);

      if (!hasStoredStockUnit) {
        log.debug("WorkOrder {} has no stored StockUnits. Line is not fully stored.", wo.getId());
        allOutputsStored = false;
        break; // Fail fast
      }
    }

    if (allOutputsStored) {
      log.info(
          "All outputs for salesOrderLineId={} are stored. Publishing SalesOrderLineStoredEvent.",
          salesOrderLineId);
      eventPublisher.publish(
          new SalesOrderLineStoredEvent(tenantId, salesOrderLineId, triggeredByStockUnitId));
    }
  }
}
