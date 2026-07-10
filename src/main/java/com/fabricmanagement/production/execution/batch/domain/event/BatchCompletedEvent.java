package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a batch reaches terminal completion (status DEPLETED). Used to snapshot
 * certifications for GOTS TC: freeze cert number and valid-until at completion time.
 */
@Getter
public class BatchCompletedEvent extends DomainEvent {
  private final UUID batchId;

  @JsonCreator
  public BatchCompletedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_COMPLETED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
  }

  public BatchCompletedEvent(UUID tenantId, UUID batchId) {
    super(tenantId, "BATCH_COMPLETED");
    this.batchId = batchId;
  }
}
