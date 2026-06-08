package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.app.SalesOrderLineStorageCheckService;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitCreatedEvent;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockUnitCreatedStorageListener {

  private final SalesOrderLineStorageCheckService storageCheckService;
  private final BatchRepository batchRepository;
  private final WorkOrderRepository workOrderRepository;
  private final IdempotentEventHandler idempotentHandler;

  /**
   * Listens to StockUnitCreatedEvent and triggers the storage check to see if all outputs for a
   * SalesOrderLine have been stored. This listener runs AFTER_COMMIT.
   */
  @ApplicationModuleListener
  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onStockUnitCreated(StockUnitCreatedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onStockUnitCreated",
        () -> {
          // 1. Resolve batch → source check
          Batch batch =
              batchRepository
                  .findByIdAndTenantId(event.getBatchId(), event.getTenantId())
                  .orElse(null);
          if (batch == null || batch.getSourceType() != BatchSourceType.INTERNAL_PRODUCTION) {
            return; // Only production outputs trigger storage check
          }

          // 2. Resolve workOrder → salesOrderLineId
          WorkOrder wo =
              workOrderRepository
                  .findByIdAndTenantIdAndIsActiveTrue(batch.getSourceId(), event.getTenantId())
                  .orElse(null);
          if (wo == null || wo.getSalesOrderLineId() == null) {
            return; // Standalone WO, not linked to a sales order
          }

          // 3. Delegate to check service (REQUIRES_NEW — already post-commit, safe to query)
          storageCheckService.publishLineStoredIfAllOutputsStored(
              event.getTenantId(), wo.getSalesOrderLineId(), event.getStockUnitId());
        });
  }

  @Recover
  public void recoverStockUnitCreated(Exception ex, StockUnitCreatedEvent event) {
    log.error(
        "Failed to process StockUnitCreatedEvent after retries. stockUnitId={}: {}",
        event.getStockUnitId(),
        ex.getMessage(),
        ex);
    throw new RuntimeException("Event processing failed after retries", ex);
  }
}
