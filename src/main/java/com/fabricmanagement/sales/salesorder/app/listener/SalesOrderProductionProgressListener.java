package com.fabricmanagement.sales.salesorder.app.listener;

import com.fabricmanagement.common.domain.event.production.SalesOrderLineProductionCompletedEvent;
import com.fabricmanagement.common.domain.event.production.SalesOrderLineStoredEvent;
import com.fabricmanagement.common.domain.event.production.WorkOrderStartedEvent;
import com.fabricmanagement.sales.salesorder.app.ProductionProgressService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesOrderProductionProgressListener {

  private final ProductionProgressService productionProgressService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onWorkOrderStarted(WorkOrderStartedEvent event) {
    log.debug(
        "Handling WorkOrderStartedEvent: workOrderId={}, salesOrderLineId={}",
        event.getWorkOrderId(),
        event.getSalesOrderLineId());

    UUID salesOrderId = productionProgressService.markLineInProduction(event.getSalesOrderLineId());
    if (salesOrderId == null) {
      return;
    }

    productionProgressService.markOrderInProgressIfConfirmed(salesOrderId);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onSalesOrderLineProductionCompleted(SalesOrderLineProductionCompletedEvent event) {
    log.debug(
        "Handling SalesOrderLineProductionCompletedEvent: salesOrderLineId={}, "
            + "completedByWorkOrderId={}",
        event.getSalesOrderLineId(),
        event.getCompletedByWorkOrderId());

    productionProgressService.markLineProductionCompleted(event.getSalesOrderLineId());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onSalesOrderLineStored(SalesOrderLineStoredEvent event) {
    productionProgressService.markLineInWarehouse(event.getSalesOrderLineId());
  }

  @Recover
  public void recoverWorkOrderStarted(Exception ex, WorkOrderStartedEvent event) {
    log.error(
        "Failed to process WorkOrderStartedEvent after retries. workOrderId={}, "
            + "salesOrderLineId={}: {}",
        event.getWorkOrderId(),
        event.getSalesOrderLineId(),
        ex.getMessage(),
        ex);
  }

  @Recover
  public void recoverSalesOrderLineProductionCompleted(
      Exception ex, SalesOrderLineProductionCompletedEvent event) {
    log.error(
        "Failed to process SalesOrderLineProductionCompletedEvent after retries. "
            + "salesOrderLineId={}, completedByWorkOrderId={}: {}",
        event.getSalesOrderLineId(),
        event.getCompletedByWorkOrderId(),
        ex.getMessage(),
        ex);
  }

  @Recover
  public void recoverSalesOrderLineStored(Exception ex, SalesOrderLineStoredEvent event) {
    log.error(
        "Failed to process SalesOrderLineStoredEvent after retries. salesOrderLineId={}: {}",
        event.getSalesOrderLineId(),
        ex.getMessage(),
        ex);
  }
}
