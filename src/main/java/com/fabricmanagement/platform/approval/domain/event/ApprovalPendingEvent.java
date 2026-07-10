package com.fabricmanagement.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** Onay talebi oluştu — HIGH. Onay bekleyen kişiye bildirim. */
@Getter
public class ApprovalPendingEvent extends DomainEvent {

  private final UUID approvalRequestId;
  private final String entityType; // WORK_ORDER, RFQ, etc.
  private final UUID entityId;
  private final String entityCode;

  /** Tekil onaylayıcı (opsiyonel); çoğu akışta {@link #notifyRecipientIds} kullanılır. */
  private final UUID approverId;

  /** Politika rolüne göre çözümlenen alıcılar — {@code notification_queue.recipient_id} için. */
  private final List<UUID> notifyRecipientIds;

  public ApprovalPendingEvent(
      UUID tenantId,
      UUID approvalRequestId,
      String entityType,
      UUID entityId,
      String entityCode,
      UUID approverId,
      List<UUID> notifyRecipientIds) {
    super(tenantId, "APPROVAL_PENDING");
    this.approvalRequestId = approvalRequestId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityCode = entityCode;
    this.approverId = approverId;
    this.notifyRecipientIds =
        notifyRecipientIds != null ? List.copyOf(notifyRecipientIds) : List.of();
  }

  @JsonCreator
  public ApprovalPendingEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("approvalRequestId") UUID approvalRequestId,
      @JsonProperty("entityType") String entityType,
      @JsonProperty("entityId") UUID entityId,
      @JsonProperty("entityCode") String entityCode,
      @JsonProperty("approverId") UUID approverId,
      @JsonProperty("notifyRecipientIds") List<UUID> notifyRecipientIds) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "APPROVAL_PENDING",
        occurredAt,
        correlationId);
    this.approvalRequestId = approvalRequestId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityCode = entityCode;
    this.approverId = approverId;
    this.notifyRecipientIds =
        notifyRecipientIds != null ? List.copyOf(notifyRecipientIds) : List.of();
  }
}
