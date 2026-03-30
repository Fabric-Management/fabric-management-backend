package com.fabricmanagement.logistics.shipment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event triggered when a shipment departs (picked up). Used by IWM to deduct inventory and create
 * transit stock.
 */
@Getter
public class ShipmentPickedUpEvent extends DomainEvent {
  private final UUID shipmentId;

  public ShipmentPickedUpEvent(UUID tenantId, UUID shipmentId) {
    super(tenantId, "SHIPMENT_PICKED_UP");
    this.shipmentId = shipmentId;
  }
}
