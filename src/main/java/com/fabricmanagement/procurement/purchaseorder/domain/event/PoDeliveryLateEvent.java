package com.fabricmanagement.procurement.purchaseorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** PO teslimatı gecikiyor — HIGH önem. Tedarik sorumlusuna bildirim. */
@Getter
public class PoDeliveryLateEvent extends DomainEvent {

  private final UUID purchaseOrderId;
  private final String poNumber;
  private final UUID supplierId;
  private final String supplierName;
  private final Instant expectedDeliveryAt;
  private final int lateDays;

  @JsonCreator
  public PoDeliveryLateEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("purchaseOrderId") UUID purchaseOrderId,
      @JsonProperty("poNumber") String poNumber,
      @JsonProperty("supplierId") UUID supplierId,
      @JsonProperty("supplierName") String supplierName,
      @JsonProperty("expectedDeliveryAt") Instant expectedDeliveryAt,
      @JsonProperty("lateDays") int lateDays) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PO_DELIVERY_LATE",
        occurredAt,
        correlationId);
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.expectedDeliveryAt = expectedDeliveryAt;
    this.lateDays = lateDays;
  }

  public PoDeliveryLateEvent(
      UUID tenantId,
      UUID purchaseOrderId,
      String poNumber,
      UUID supplierId,
      String supplierName,
      Instant expectedDeliveryAt,
      int lateDays) {
    super(tenantId, "PO_DELIVERY_LATE");
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.expectedDeliveryAt = expectedDeliveryAt;
    this.lateDays = lateDays;
  }
}
