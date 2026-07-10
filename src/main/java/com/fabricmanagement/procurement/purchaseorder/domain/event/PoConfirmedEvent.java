package com.fabricmanagement.procurement.purchaseorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** PO tedarikçi tarafından onaylandı — NORMAL. */
@Getter
public class PoConfirmedEvent extends DomainEvent {

  private final UUID purchaseOrderId;
  private final String poNumber;
  private final UUID supplierId;

  @JsonCreator
  public PoConfirmedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("purchaseOrderId") UUID purchaseOrderId,
      @JsonProperty("poNumber") String poNumber,
      @JsonProperty("supplierId") UUID supplierId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PO_CONFIRMED",
        occurredAt,
        correlationId);
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.supplierId = supplierId;
  }

  public PoConfirmedEvent(UUID tenantId, UUID purchaseOrderId, String poNumber, UUID supplierId) {
    super(tenantId, "PO_CONFIRMED");
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.supplierId = supplierId;
  }
}
