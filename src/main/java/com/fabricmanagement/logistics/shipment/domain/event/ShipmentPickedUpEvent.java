package com.fabricmanagement.logistics.shipment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event triggered when a shipment departs (picked up). Used by IWM to deduct inventory and create
 * transit stock.
 */
@Getter
public class ShipmentPickedUpEvent extends DomainEvent {
  private final UUID shipmentId;

  @JsonCreator
  public ShipmentPickedUpEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("shipmentId") UUID shipmentId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SHIPMENT_PICKED_UP",
        occurredAt,
        correlationId);
    this.shipmentId = shipmentId;
  }

  public ShipmentPickedUpEvent(UUID tenantId, UUID shipmentId) {
    super(tenantId, "SHIPMENT_PICKED_UP");
    this.shipmentId = shipmentId;
  }
}
