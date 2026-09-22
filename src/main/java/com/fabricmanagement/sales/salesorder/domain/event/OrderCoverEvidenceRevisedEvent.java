package com.fabricmanagement.sales.salesorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class OrderCoverEvidenceRevisedEvent extends DomainEvent {
  private final UUID caseId;
  private final UUID evidenceId;
  private final long revision;

  public OrderCoverEvidenceRevisedEvent(
      UUID tenantId, UUID caseId, UUID evidenceId, long revision) {
    super(tenantId, "ORDER_COVER_EVIDENCE_REVISED");
    this.caseId = caseId;
    this.evidenceId = evidenceId;
    this.revision = revision;
  }

  @JsonCreator
  public OrderCoverEvidenceRevisedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("caseId") UUID caseId,
      @JsonProperty("evidenceId") UUID evidenceId,
      @JsonProperty("revision") long revision) {
    super(
        eventId,
        tenantId,
        eventType == null ? "ORDER_COVER_EVIDENCE_REVISED" : eventType,
        occurredAt,
        correlationId);
    this.caseId = caseId;
    this.evidenceId = evidenceId;
    this.revision = revision;
  }
}
