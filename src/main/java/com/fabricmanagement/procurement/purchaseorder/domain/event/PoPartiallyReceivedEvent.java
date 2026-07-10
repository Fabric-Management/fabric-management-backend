package com.fabricmanagement.procurement.purchaseorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** PO kısmi teslim alındı — NORMAL. */
@Getter
public class PoPartiallyReceivedEvent extends DomainEvent {

  private final UUID purchaseOrderId;
  private final String poNumber;
  private final int receivedItemCount;
  private final int totalItemCount;

  @JsonCreator
  public PoPartiallyReceivedEvent(
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
        eventType != null ? eventType : "PO_PARTIALLY_RECEIVED",
        occurredAt,
        correlationId);
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.receivedItemCount = receivedItemCount;
    this.totalItemCount = totalItemCount;
  }

  public PoPartiallyReceivedEvent(
      UUID tenantId,
      UUID purchaseOrderId,
      String poNumber,
      int receivedItemCount,
      int totalItemCount) {
    super(tenantId, "PO_PARTIALLY_RECEIVED");
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.receivedItemCount = receivedItemCount;
    this.totalItemCount = totalItemCount;
  }
}
