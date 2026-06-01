package com.fabricmanagement.common.domain.event.production;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** Published after all active WorkOrders linked to a SalesOrderLine are completed. */
@Getter
public class SalesOrderLineProductionCompletedEvent extends DomainEvent {

  private final UUID salesOrderLineId;
  private final UUID completedByWorkOrderId;

  public SalesOrderLineProductionCompletedEvent(
      UUID tenantId, UUID salesOrderLineId, UUID completedByWorkOrderId) {
    super(tenantId, "SALES_ORDER_LINE_PRODUCTION_COMPLETED");
    this.salesOrderLineId = salesOrderLineId;
    this.completedByWorkOrderId = completedByWorkOrderId;
  }
}
