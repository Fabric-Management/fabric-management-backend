package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchTransferredEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID oldLocationId;
  private final UUID newLocationId;
  private final String remarks;

  @JsonCreator
  public BatchTransferredEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("quantity") BigDecimal quantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("oldLocationId") UUID oldLocationId,
      @JsonProperty("newLocationId") UUID newLocationId,
      @JsonProperty("remarks") String remarks) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_TRANSFERRED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.oldLocationId = oldLocationId;
    this.newLocationId = newLocationId;
    this.remarks = remarks;
  }

  public BatchTransferredEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      UUID oldLocationId,
      UUID newLocationId,
      String remarks) {
    super(tenantId, "BATCH_TRANSFERRED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.oldLocationId = oldLocationId;
    this.newLocationId = newLocationId;
    this.remarks = remarks;
  }
}
