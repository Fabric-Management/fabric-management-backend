package com.fabricmanagement.procurement.purchaseorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Purchase order fully received. Domain event only; F-20 intentionally adds no notification. */
@Getter
public class PoReceivedEvent extends DomainEvent {

  private final UUID purchaseOrderId;
  private final String poNumber;
  private final int receivedItemCount;
  private final int totalItemCount;

  @JsonCreator
  public PoReceivedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("purchaseOrderId") UUID purchaseOrderId,
      @JsonProperty("poNumber") String poNumber,
      @JsonProperty("receivedItemCount") int receivedItemCount,
      @JsonProperty("totalItemCount") int totalItemCount) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PO_RECEIVED",
        occurredAt,
        correlationId);
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.receivedItemCount = receivedItemCount;
    this.totalItemCount = totalItemCount;
  }

  public PoReceivedEvent(
      UUID tenantId,
      UUID purchaseOrderId,
      String poNumber,
      int receivedItemCount,
      int totalItemCount) {
    super(tenantId, "PO_RECEIVED");
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.receivedItemCount = receivedItemCount;
    this.totalItemCount = totalItemCount;
  }
}
