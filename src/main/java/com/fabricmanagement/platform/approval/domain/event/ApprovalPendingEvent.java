package com.fabricmanagement.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** Onay talebi oluştu — HIGH. Onay bekleyen kişiye bildirim. */
@Getter
public class ApprovalPendingEvent extends DomainEvent {

  private final UUID approvalRequestId;
  private final String entityType; // WORK_ORDER, RFQ, etc.
  private final UUID entityId;
  private final String entityCode;
  private final UUID approverId;

  public ApprovalPendingEvent(
      UUID tenantId,
      UUID approvalRequestId,
      String entityType,
      UUID entityId,
      String entityCode,
      UUID approverId) {
    super(tenantId, "APPROVAL_PENDING");
    this.approvalRequestId = approvalRequestId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityCode = entityCode;
    this.approverId = approverId;
  }
}
