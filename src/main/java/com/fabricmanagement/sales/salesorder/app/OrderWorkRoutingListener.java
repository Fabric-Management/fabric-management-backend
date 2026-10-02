package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A confirmed order goes into fulfilment: its arrival estimate is routed to shipping in the same
 * transaction as the confirmation, unassigned until someone takes it.
 */
@Component
@RequiredArgsConstructor
public class OrderWorkRoutingListener {

  private final OrderWorkService work;

  @EventListener
  public void onConfirmed(SalesOrderConfirmedEvent event) {
    work.route(event.getSalesOrderId(), OrderWorkKind.ARRIVAL_ESTIMATE, null);
  }
}
