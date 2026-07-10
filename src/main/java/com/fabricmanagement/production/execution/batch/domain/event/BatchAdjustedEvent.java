package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchAdjustedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal delta;
  private final String unit;
  private final UUID locationId;
  private final String reason;
  private final String remarks;

  @JsonCreator
  public BatchAdjustedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("delta") BigDecimal delta,
      @JsonProperty("unit") String unit,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("reason") String reason,
      @JsonProperty("remarks") String remarks) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_ADJUSTED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.delta = delta;
    this.unit = unit;
    this.locationId = locationId;
    this.reason = reason;
    this.remarks = remarks;
  }

  public BatchAdjustedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal delta,
      String unit,
      UUID locationId,
      String reason,
      String remarks) {
    super(tenantId, "BATCH_ADJUSTED");
    this.batchId = batchId;
    this.delta = delta;
    this.unit = unit;
    this.locationId = locationId;
    this.reason = reason;
    this.remarks = remarks;
  }
}
