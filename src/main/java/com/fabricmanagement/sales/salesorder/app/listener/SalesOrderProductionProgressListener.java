package com.fabricmanagement.sales.salesorder.app.listener;

import com.fabricmanagement.common.domain.event.production.SalesOrderLineProductionCompletedEvent;
import com.fabricmanagement.common.domain.event.production.SalesOrderLineStoredEvent;
import com.fabricmanagement.common.domain.event.production.WorkOrderStartedEvent;
import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.sales.salesorder.app.ProductionProgressService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesOrderProductionProgressListener {

  private final ProductionProgressService productionProgressService;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onWorkOrderStarted(WorkOrderStartedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onWorkOrderStarted",
        () -> {
          log.debug(
              "Handling WorkOrderStartedEvent: workOrderId={}, salesOrderLineId={}",
              event.getWorkOrderId(),
              event.getSalesOrderLineId());

          UUID salesOrderId =
              productionProgressService.markLineInProduction(event.getSalesOrderLineId());
          if (salesOrderId == null) {
            return;
          }

          productionProgressService.markOrderInProgressIfConfirmed(salesOrderId);
        });
  }

  @ApplicationModuleListener
  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onSalesOrderLineProductionCompleted(SalesOrderLineProductionCompletedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onSalesOrderLineProductionCompleted",
        () -> {
          log.debug(
              "Handling SalesOrderLineProductionCompletedEvent: salesOrderLineId={}, "
                  + "completedByWorkOrderId={}",
              event.getSalesOrderLineId(),
              event.getCompletedByWorkOrderId());

          productionProgressService.markLineProductionCompleted(event.getSalesOrderLineId());
        });
  }

  @ApplicationModuleListener
  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onSalesOrderLineStored(SalesOrderLineStoredEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onSalesOrderLineStored",
        () -> {
          productionProgressService.markLineInWarehouse(event.getSalesOrderLineId());
        });
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
    throw new RuntimeException("Event processing failed after retries", ex);
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
    throw new RuntimeException("Event processing failed after retries", ex);
  }

  @Recover
  public void recoverSalesOrderLineStored(Exception ex, SalesOrderLineStoredEvent event) {
    log.error(
        "Failed to process SalesOrderLineStoredEvent after retries. salesOrderLineId={}: {}",
        event.getSalesOrderLineId(),
        ex.getMessage(),
        ex);
    throw new RuntimeException("Event processing failed after retries", ex);
  }
}
