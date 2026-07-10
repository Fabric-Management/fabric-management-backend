package com.fabricmanagement.production.execution.lineage.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class BatchLineageDeletedEvent extends DomainEvent {

  private final UUID lineageId;
  private final UUID parentBatchId;
  private final UUID childBatchId;

  @JsonCreator
  public BatchLineageDeletedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("lineageId") UUID lineageId,
      @JsonProperty("parentBatchId") UUID parentBatchId,
      @JsonProperty("childBatchId") UUID childBatchId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_LINEAGE_DELETED",
        occurredAt,
        correlationId);
    this.lineageId = lineageId;
    this.parentBatchId = parentBatchId;
    this.childBatchId = childBatchId;
  }

  @Builder
  public BatchLineageDeletedEvent(
      UUID tenantId, UUID lineageId, UUID parentBatchId, UUID childBatchId) {
    super(tenantId, "BATCH_LINEAGE_DELETED");
    this.lineageId = lineageId;
    this.parentBatchId = parentBatchId;
    this.childBatchId = childBatchId;
  }
}
