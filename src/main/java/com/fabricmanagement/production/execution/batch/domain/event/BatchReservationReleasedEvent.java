package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchReservationReleasedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal releasedQuantity;
  private final String unit;
  private final UUID locationId;
  private final UUID reservationId;

  @JsonCreator
  public BatchReservationReleasedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("releasedQuantity") BigDecimal releasedQuantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("reservationId") UUID reservationId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_RESERVATION_RELEASED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.releasedQuantity = releasedQuantity;
    this.unit = unit;
    this.locationId = locationId;
    this.reservationId = reservationId;
  }

  public BatchReservationReleasedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal releasedQuantity,
      String unit,
      UUID locationId,
      UUID reservationId) {
    super(tenantId, "BATCH_RESERVATION_RELEASED");
    this.batchId = batchId;
    this.releasedQuantity = releasedQuantity;
    this.unit = unit;
    this.locationId = locationId;
    this.reservationId = reservationId;
  }
}
