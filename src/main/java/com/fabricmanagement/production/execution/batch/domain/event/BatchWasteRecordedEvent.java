package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.batch.domain.WasteCategory;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchWasteRecordedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID locationId;
  private final WasteCategory wasteCategory;
  private final String reason;

  @JsonCreator
  public BatchWasteRecordedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("quantity") BigDecimal quantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("wasteCategory") WasteCategory wasteCategory,
      @JsonProperty("reason") String reason) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_WASTE_RECORDED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
    this.wasteCategory = wasteCategory;
    this.reason = reason;
  }

  public BatchWasteRecordedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      WasteCategory wasteCategory,
      String reason) {
    super(tenantId, "BATCH_WASTE_RECORDED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
    this.wasteCategory = wasteCategory;
    this.reason = reason;
  }
}
