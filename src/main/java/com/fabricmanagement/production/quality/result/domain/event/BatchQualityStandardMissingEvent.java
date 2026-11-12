package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when no quality profile applies to a tested batch, so its result needs a person's
 * review. {@code reasonCode} names why (for example {@code BATCH_COMPOSITION_UNKNOWN}).
 */
@Getter
public class BatchQualityStandardMissingEvent extends DomainEvent {

  private final UUID batchId;
  private final String batchCode;
  private final String target;
  private final String reasonCode;

  public BatchQualityStandardMissingEvent(
      UUID tenantId, UUID batchId, String batchCode, String target, String reasonCode) {
    super(tenantId, "BATCH_QUALITY_STANDARD_MISSING");
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.target = target;
    this.reasonCode = reasonCode;
  }

  @JsonCreator
  public BatchQualityStandardMissingEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("batchCode") String batchCode,
      @JsonProperty("target") String target,
      @JsonProperty("reasonCode") String reasonCode) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_QUALITY_STANDARD_MISSING",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.target = target;
    this.reasonCode = reasonCode;
  }
}
