package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkOrderSalesEventListener {

  private final WorkOrderService workOrderService;

  @Async
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
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
                  new com.fabricmanagement.production.execution.workorder.dto
                      .IncomingSalesOrderLine(
                      line.lineId(),
                      line.productCode(),
                      line.quantity(),
                      line.unit(),
                      line.requestedDeliveryDate());
              workOrderService.createFromSalesOrderLine(
                  event.getTenantId(), event.getSalesOrderId(), incomingLine);
            });
  }
}
