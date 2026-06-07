package com.fabricmanagement.production.execution.workorder.app.listener;

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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkOrderSalesEventListener {

  private final WorkOrderService workOrderService;
  private final WorkOrderRepository workOrderRepository;

  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSalesOrderConfirmed(SalesOrderConfirmedEvent event) {
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
  }

  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSalesOrderCancelled(SalesOrderCancelledEvent event) {
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
                          workOrderService.changeStatus(wo.getId(), WorkOrderStatus.CANCELLED);
                          log.info(
                              "Cancelled WorkOrder {} (was {}) for salesOrderLine {}",
                              wo.getWorkOrderNumber(),
                              wo.getStatus(),
                              lineId);
                        } catch (WorkOrderDomainException
                            | ObjectOptimisticLockingFailureException e) {
                          // Idempotent: already CANCELLED, transition not valid, or concurrent
                          // status change by production. Per-WO swallow ensures one WO's conflict
                          // doesn't rollback other WOs' cancellations.
                          log.warn(
                              "Could not cancel WorkOrder {} (status {}): {}",
                              wo.getWorkOrderNumber(),
                              wo.getStatus(),
                              e.getMessage());
                        }
                      });
            });
  }
}
