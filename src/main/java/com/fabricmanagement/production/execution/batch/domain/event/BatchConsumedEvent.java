package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchConsumedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID locationId;
  private final UUID referenceId;
  private final String referenceType;

  @JsonCreator
  public BatchConsumedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("quantity") BigDecimal quantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("referenceId") UUID referenceId,
      @JsonProperty("referenceType") String referenceType) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_CONSUMED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
    this.referenceId = referenceId;
    this.referenceType = referenceType;
  }

  public BatchConsumedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      UUID referenceId,
      String referenceType) {
    super(tenantId, "BATCH_CONSUMED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
    this.referenceId = referenceId;
    this.referenceType = referenceType;
  }
}
