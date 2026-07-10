package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** Published after a blended batch is created (multiple parents consumed → one child batch). */
@Getter
public class BlendedBatchCreatedEvent extends DomainEvent {
  private final UUID childBatchId;
  private final List<UUID> parentIds;
  private final BigDecimal totalQuantity;
  private final String unit;

  @JsonCreator
  public BlendedBatchCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("childBatchId") UUID childBatchId,
      @JsonProperty("parentIds") List<UUID> parentIds,
      @JsonProperty("totalQuantity") BigDecimal totalQuantity,
      @JsonProperty("unit") String unit) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BLENDED_BATCH_CREATED",
        occurredAt,
        correlationId);
    this.childBatchId = childBatchId;
    this.parentIds = parentIds != null ? List.copyOf(parentIds) : List.of();
    this.totalQuantity = totalQuantity;
    this.unit = unit;
  }

  public BlendedBatchCreatedEvent(
      UUID tenantId,
      UUID childBatchId,
      List<UUID> parentIds,
      BigDecimal totalQuantity,
      String unit) {
    super(tenantId, "BLENDED_BATCH_CREATED");
    this.childBatchId = childBatchId;
    this.parentIds = parentIds != null ? List.copyOf(parentIds) : List.of();
    this.totalQuantity = totalQuantity;
    this.unit = unit;
  }
}
