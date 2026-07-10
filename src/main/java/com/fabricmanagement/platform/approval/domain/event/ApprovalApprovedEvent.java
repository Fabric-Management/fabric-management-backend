package com.fabricmanagement.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Onay verildi — NORMAL. */
@Getter
public class ApprovalApprovedEvent extends DomainEvent {

  private final UUID approvalRequestId;
  private final String entityType;
  private final UUID entityId;
  private final String entityCode;
  private final UUID requesterId;

  public ApprovalApprovedEvent(
      UUID tenantId,
      UUID approvalRequestId,
      String entityType,
      UUID entityId,
      String entityCode,
      UUID requesterId) {
    super(tenantId, "APPROVAL_APPROVED");
    this.approvalRequestId = approvalRequestId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityCode = entityCode;
    this.requesterId = requesterId;
  }

  @JsonCreator
  public ApprovalApprovedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("approvalRequestId") UUID approvalRequestId,
      @JsonProperty("entityType") String entityType,
      @JsonProperty("entityId") UUID entityId,
      @JsonProperty("entityCode") String entityCode,
      @JsonProperty("requesterId") UUID requesterId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "APPROVAL_APPROVED",
        occurredAt,
        correlationId);
    this.approvalRequestId = approvalRequestId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityCode = entityCode;
    this.requesterId = requesterId;
  }
}
