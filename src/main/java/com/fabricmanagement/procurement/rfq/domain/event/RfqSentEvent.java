package com.fabricmanagement.procurement.rfq.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** RFQ tedarikçilere gönderildi — NORMAL. */
@Getter
public class RfqSentEvent extends DomainEvent {

  private final UUID rfqId;
  private final String rfqNumber;
  private final List<UUID> supplierIds;

  @JsonCreator
  public RfqSentEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("rfqId") UUID rfqId,
      @JsonProperty("rfqNumber") String rfqNumber,
      @JsonProperty("supplierIds") List<UUID> supplierIds) {
    super(eventId, tenantId, eventType != null ? eventType : "RFQ_SENT", occurredAt, correlationId);
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.supplierIds = supplierIds != null ? List.copyOf(supplierIds) : List.of();
  }

  public RfqSentEvent(UUID tenantId, UUID rfqId, String rfqNumber, List<UUID> supplierIds) {
    super(tenantId, "RFQ_SENT");
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.supplierIds = supplierIds != null ? List.copyOf(supplierIds) : List.of();
  }
}
