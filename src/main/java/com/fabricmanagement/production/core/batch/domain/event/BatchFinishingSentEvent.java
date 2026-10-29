package com.fabricmanagement.production.core.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Published when a fabric lot goes out for a finishing process (FABRIC-3b). */
@Getter
public class BatchFinishingSentEvent extends DomainEvent {
  private final UUID batchId;
  private final UUID stepId;
  private final UUID processId;
  private final UUID partnerId;
  private final String firmJobNumber;
  private final BigDecimal sentMetres;

  @JsonCreator
  public BatchFinishingSentEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("stepId") UUID stepId,
      @JsonProperty("processId") UUID processId,
      @JsonProperty("partnerId") UUID partnerId,
      @JsonProperty("firmJobNumber") String firmJobNumber,
      @JsonProperty("sentMetres") BigDecimal sentMetres) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_FINISHING_SENT",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.stepId = stepId;
    this.processId = processId;
    this.partnerId = partnerId;
    this.firmJobNumber = firmJobNumber;
    this.sentMetres = sentMetres;
  }

  public BatchFinishingSentEvent(
      UUID tenantId,
      UUID batchId,
      UUID stepId,
      UUID processId,
      UUID partnerId,
      String firmJobNumber,
      BigDecimal sentMetres) {
    super(tenantId, "BATCH_FINISHING_SENT");
    this.batchId = batchId;
    this.stepId = stepId;
    this.processId = processId;
    this.partnerId = partnerId;
    this.firmJobNumber = firmJobNumber;
    this.sentMetres = sentMetres;
  }
}
