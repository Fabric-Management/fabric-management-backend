package com.fabricmanagement.logistics.shipment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
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

  @JsonCreator
  public ShipmentLineConfirmedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("shipmentLineId") UUID shipmentLineId,
      @JsonProperty("salesOrderLineId") UUID salesOrderLineId,
      @JsonProperty("confirmedQuantity") BigDecimal confirmedQuantity) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SHIPMENT_LINE_CONFIRMED",
        occurredAt,
        correlationId);
    this.shipmentLineId = shipmentLineId;
    this.salesOrderLineId = salesOrderLineId;
    this.confirmedQuantity = confirmedQuantity;
  }
}
