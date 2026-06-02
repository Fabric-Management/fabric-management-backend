package com.fabricmanagement.common.domain.event.production;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Published by the production module when all outputs for a sales order line have been fully stored
 * in the warehouse. Used to trigger SalesOrderLine status transition to IN_WAREHOUSE.
 */
@Getter
public class SalesOrderLineStoredEvent extends DomainEvent {

  private final UUID salesOrderLineId;
  private final UUID triggeredByStockUnitId;

  public SalesOrderLineStoredEvent(
      UUID tenantId, UUID salesOrderLineId, UUID triggeredByStockUnitId) {
    super(tenantId, "SALES_ORDER_LINE_STORED");
    this.salesOrderLineId = salesOrderLineId;
    this.triggeredByStockUnitId = triggeredByStockUnitId;
  }
}
