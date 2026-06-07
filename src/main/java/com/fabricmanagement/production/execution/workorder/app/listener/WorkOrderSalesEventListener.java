package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.IncomingSalesOrderLine;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkOrderSalesEventListener {

  private final WorkOrderService workOrderService;
  private final WorkOrderRepository workOrderRepository;
  private final IdempotentEventHandler idempotentHandler;

  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  @ApplicationModuleListener
  public void onSalesOrderConfirmed(SalesOrderConfirmedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onSalesOrderConfirmed",
        () -> {
          log.info(
              "Received SalesOrderConfirmedEvent for orderId={}, creating WorkOrders...",
              event.getSalesOrderId());

          event
              .getLines()
              .forEach(
                  line -> {
                    var incomingLine =
                        new IncomingSalesOrderLine(
                            line.lineId(),
                            line.productCode(),
                            line.quantity(),
                            line.unit(),
                            line.requestedDeliveryDate());
                    workOrderService.createFromSalesOrderLine(
                        event.getTenantId(), event.getSalesOrderId(), incomingLine);
                  });
        });
  }

  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  @ApplicationModuleListener
  public void onSalesOrderCancelled(SalesOrderCancelledEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onSalesOrderCancelled",
        () -> {
          log.info(
              "Received SalesOrderCancelledEvent for orderId={}, cascading WO cancellation...",
              event.getSalesOrderId());

          event
              .getCancelledLineIds()
              .forEach(
                  lineId -> {
                    List<WorkOrder> workOrders =
                        workOrderRepository
                            .findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
                                event.getTenantId(), lineId);

                    workOrders.stream()
                        .filter(
                            wo ->
                                wo.getStatus() != WorkOrderStatus.COMPLETED
                                    && wo.getStatus() != WorkOrderStatus.CANCELLED)
                        .forEach(
                            wo -> {
                              try {
                                workOrderService.changeStatus(
                                    wo.getId(), WorkOrderStatus.CANCELLED);
                                log.info(
                                    "Cancelled WorkOrder {} (was {}) for salesOrderLine {}",
                                    wo.getWorkOrderNumber(),
                                    wo.getStatus(),
                                    lineId);
                              } catch (WorkOrderDomainException
                                  | ObjectOptimisticLockingFailureException e) {
                                // Idempotent: already CANCELLED, transition not valid, or
                                // concurrent
                                // status change by production. Per-WO swallow ensures one WO's
                                // conflict
                                // doesn't rollback other WOs' cancellations.
                                log.warn(
                                    "Could not cancel WorkOrder {} (status {}): {}",
                                    wo.getWorkOrderNumber(),
                                    wo.getStatus(),
                                    e.getMessage());
                              }
                            });
                  });
        });
  }
}
