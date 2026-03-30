package com.fabricmanagement.logistics.shipment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/**
 * Event published when a shipment line is confirmed and shipped. Used by the Sales module to update
 * the shippedQuantity on the SalesOrderLine.
 */
@Getter
public class ShipmentLineConfirmedEvent extends DomainEvent {

  private final UUID shipmentLineId;
  private final UUID salesOrderLineId;
  private final BigDecimal confirmedQuantity;

  public ShipmentLineConfirmedEvent(
      UUID tenantId, UUID shipmentLineId, UUID salesOrderLineId, BigDecimal confirmedQuantity) {
    super(tenantId, "SHIPMENT_LINE_CONFIRMED");
    this.shipmentLineId = shipmentLineId;
    this.salesOrderLineId = salesOrderLineId;
    this.confirmedQuantity = confirmedQuantity;
  }
}
