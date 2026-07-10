package com.fabricmanagement.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Onay reddedildi — HIGH. Talep sahibine bildirim. */
@Getter
public class ApprovalRejectedEvent extends DomainEvent {

  private final UUID approvalRequestId;
  private final String entityType;
  private final UUID entityId;
  private final String entityCode;
  private final UUID requesterId;
  private final String rejectionReason;

  public ApprovalRejectedEvent(
      UUID tenantId,
      UUID approvalRequestId,
      String entityType,
      UUID entityId,
      String entityCode,
      UUID requesterId,
      String rejectionReason) {
    super(tenantId, "APPROVAL_REJECTED");
    this.approvalRequestId = approvalRequestId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityCode = entityCode;
    this.requesterId = requesterId;
    this.rejectionReason = rejectionReason;
  }

  @JsonCreator
  public ApprovalRejectedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("approvalRequestId") UUID approvalRequestId,
      @JsonProperty("entityType") String entityType,
      @JsonProperty("entityId") UUID entityId,
      @JsonProperty("entityCode") String entityCode,
      @JsonProperty("requesterId") UUID requesterId,
      @JsonProperty("rejectionReason") String rejectionReason) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "APPROVAL_REJECTED",
        occurredAt,
        correlationId);
    this.approvalRequestId = approvalRequestId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityCode = entityCode;
    this.requesterId = requesterId;
    this.rejectionReason = rejectionReason;
  }
}
