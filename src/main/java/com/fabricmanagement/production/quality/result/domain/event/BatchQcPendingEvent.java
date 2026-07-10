package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Batch QC bekliyor — NORMAL. QC sorumlusuna bildirim. */
@Getter
public class BatchQcPendingEvent extends DomainEvent {

  private final UUID batchId;
  private final String batchCode;
  private final UUID qualityResponsibleUserId;

  @JsonCreator
  public BatchQcPendingEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("batchCode") String batchCode,
      @JsonProperty("qualityResponsibleUserId") UUID qualityResponsibleUserId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_QC_PENDING",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.qualityResponsibleUserId = qualityResponsibleUserId;
  }

  public BatchQcPendingEvent(
      UUID tenantId, UUID batchId, String batchCode, UUID qualityResponsibleUserId) {
    super(tenantId, "BATCH_QC_PENDING");
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.qualityResponsibleUserId = qualityResponsibleUserId;
  }
}
