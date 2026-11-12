package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a batch passes testing with conditions: someone with authority has to approve the
 * conditional acceptance (concession) before the batch can be used.
 */
@Getter
public class BatchConcessionReviewRequiredEvent extends DomainEvent {

  private final UUID batchId;
  private final String batchCode;

  public BatchConcessionReviewRequiredEvent(UUID tenantId, UUID batchId, String batchCode) {
    super(tenantId, "BATCH_CONCESSION_REVIEW_REQUIRED");
    this.batchId = batchId;
    this.batchCode = batchCode;
  }

  @JsonCreator
  public BatchConcessionReviewRequiredEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("batchCode") String batchCode) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_CONCESSION_REVIEW_REQUIRED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.batchCode = batchCode;
  }
}
