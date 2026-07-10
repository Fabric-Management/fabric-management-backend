package com.fabricmanagement.procurement.rfq.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** RFQ deadline'ına yaklaşıyor — HIGH. */
@Getter
public class RfqDeadlineApproachingEvent extends DomainEvent {

  private final UUID rfqId;
  private final String rfqNumber;
  private final int hoursRemaining;

  @JsonCreator
  public RfqDeadlineApproachingEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("rfqId") UUID rfqId,
      @JsonProperty("rfqNumber") String rfqNumber,
      @JsonProperty("hoursRemaining") int hoursRemaining) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "RFQ_DEADLINE_APPROACHING",
        occurredAt,
        correlationId);
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.hoursRemaining = hoursRemaining;
  }

  public RfqDeadlineApproachingEvent(
      UUID tenantId, UUID rfqId, String rfqNumber, int hoursRemaining) {
    super(tenantId, "RFQ_DEADLINE_APPROACHING");
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.hoursRemaining = hoursRemaining;
  }
}
