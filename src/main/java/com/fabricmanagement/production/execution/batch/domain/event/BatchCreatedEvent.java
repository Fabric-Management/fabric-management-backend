package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchCreatedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID locationId;

  @JsonCreator
  public BatchCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("quantity") BigDecimal quantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("locationId") UUID locationId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_CREATED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
  }

  public BatchCreatedEvent(
      UUID tenantId, UUID batchId, BigDecimal quantity, String unit, UUID locationId) {
    super(tenantId, "BATCH_CREATED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
  }
}
