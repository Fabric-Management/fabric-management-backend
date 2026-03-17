package com.fabricmanagement.common.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
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
}
