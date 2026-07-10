package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Batch kalite kontrolünü geçemedi — CRITICAL. Anında tüm kanallardan, alıcı tercihi değil. QC
 * sorumlusu ve departman yöneticisi hedeflenir.
 */
@Getter
public class BatchQcFailedEvent extends DomainEvent {

  private final UUID batchId;
  private final String batchCode;
  private final String failureReason;
  private final UUID qualityResponsibleUserId;

  @JsonCreator
  public BatchQcFailedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("batchCode") String batchCode,
      @JsonProperty("failureReason") String failureReason,
      @JsonProperty("qualityResponsibleUserId") UUID qualityResponsibleUserId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_QC_FAILED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.failureReason = failureReason;
    this.qualityResponsibleUserId = qualityResponsibleUserId;
  }

  public BatchQcFailedEvent(
      UUID tenantId,
      UUID batchId,
      String batchCode,
      String failureReason,
      UUID qualityResponsibleUserId) {
    super(tenantId, "BATCH_QC_FAILED");
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.failureReason = failureReason;
    this.qualityResponsibleUserId = qualityResponsibleUserId;
  }
}
