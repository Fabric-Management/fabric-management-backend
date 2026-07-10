package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchReservationCompletedEvent extends DomainEvent {
  private final UUID batchId;
  private final UUID reservationId;
  private final BigDecimal releasedQuantity;
  private final String unit;
  private final UUID locationId;

  @JsonCreator
  public BatchReservationCompletedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("reservationId") UUID reservationId,
      @JsonProperty("releasedQuantity") BigDecimal releasedQuantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("locationId") UUID locationId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_RESERVATION_COMPLETED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.reservationId = reservationId;
    this.releasedQuantity = releasedQuantity;
    this.unit = unit;
    this.locationId = locationId;
  }

  public BatchReservationCompletedEvent(
      UUID tenantId,
      UUID batchId,
      UUID reservationId,
      BigDecimal releasedQuantity,
      String unit,
      UUID locationId) {
    super(tenantId, "BATCH_RESERVATION_COMPLETED");
    this.batchId = batchId;
    this.reservationId = reservationId;
    this.releasedQuantity = releasedQuantity;
    this.unit = unit;
    this.locationId = locationId;
  }
}
