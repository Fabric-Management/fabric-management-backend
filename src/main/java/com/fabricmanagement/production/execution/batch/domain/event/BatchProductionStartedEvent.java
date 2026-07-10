package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchProductionStartedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID previousLocationId;
  private final UUID machineLocationId;
  private final String machineCode;

  @JsonCreator
  public BatchProductionStartedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("quantity") BigDecimal quantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("previousLocationId") UUID previousLocationId,
      @JsonProperty("machineLocationId") UUID machineLocationId,
      @JsonProperty("machineCode") String machineCode) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_PRODUCTION_STARTED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.previousLocationId = previousLocationId;
    this.machineLocationId = machineLocationId;
    this.machineCode = machineCode;
  }

  public BatchProductionStartedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      UUID previousLocationId,
      UUID machineLocationId,
      String machineCode) {
    super(tenantId, "BATCH_PRODUCTION_STARTED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.previousLocationId = previousLocationId;
    this.machineLocationId = machineLocationId;
    this.machineCode = machineCode;
  }
}
