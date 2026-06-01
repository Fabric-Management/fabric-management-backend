package com.fabricmanagement.sales.salesorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/**
 * Satış siparişi iptal edildiğinde yayınlanır.
 *
 * <p>WorkOrderSalesEventListener bu eventi dinler ve ilgili üretim emirlerini (COMPLETED
 * olmayanları) iptal eder (cascade iptal).
 */
@Getter
public class SalesOrderCancelledEvent extends DomainEvent {

  private final UUID salesOrderId;
  private final String orderNumber;
  private final List<UUID> cancelledLineIds;

  public SalesOrderCancelledEvent(
      UUID tenantId, UUID salesOrderId, String orderNumber, List<UUID> cancelledLineIds) {
    super(tenantId, "SalesOrderCancelled");
    this.salesOrderId = salesOrderId;
    this.orderNumber = orderNumber;
    this.cancelledLineIds = cancelledLineIds != null ? cancelledLineIds : List.of();
  }
}
