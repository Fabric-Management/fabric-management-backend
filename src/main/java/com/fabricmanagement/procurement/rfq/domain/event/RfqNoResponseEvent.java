package com.fabricmanagement.procurement.rfq.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** RFQ'ya tedarikçi yanıt vermedi — HIGH. */
@Getter
public class RfqNoResponseEvent extends DomainEvent {

  private final UUID rfqId;
  private final String rfqNumber;
  private final UUID supplierId;
  private final String supplierName;

  @JsonCreator
  public RfqNoResponseEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("rfqId") UUID rfqId,
      @JsonProperty("rfqNumber") String rfqNumber,
      @JsonProperty("supplierId") UUID supplierId,
      @JsonProperty("supplierName") String supplierName) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "RFQ_NO_RESPONSE",
        occurredAt,
        correlationId);
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
  }

  public RfqNoResponseEvent(
      UUID tenantId, UUID rfqId, String rfqNumber, UUID supplierId, String supplierName) {
    super(tenantId, "RFQ_NO_RESPONSE");
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
  }
}
