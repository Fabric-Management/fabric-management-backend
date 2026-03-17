package com.fabricmanagement.common.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
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
}
