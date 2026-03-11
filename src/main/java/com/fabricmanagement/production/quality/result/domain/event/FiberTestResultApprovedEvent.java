package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a fiber test result receives an approval decision (APPROVED, REJECTED,
 * CONDITIONAL_ACCEPT).
 *
 * <p>Listeners (e.g. BatchStatusUpdateListener) use this to transition the associated batch from
 * PENDING_QC to AVAILABLE (on APPROVED/CONDITIONAL_ACCEPT) or QC_REJECTED (on REJECTED).
 */
@Getter
public class FiberTestResultApprovedEvent extends DomainEvent {

  private final UUID batchId;
  private final TestApprovalStatus approvalStatus;
  private final UUID actorId;

  public FiberTestResultApprovedEvent(
      UUID tenantId, UUID batchId, TestApprovalStatus approvalStatus, UUID actorId) {
    super(tenantId, "FIBER_TEST_RESULT_APPROVED");
    this.batchId = batchId;
    this.approvalStatus = approvalStatus;
    this.actorId = actorId;
  }
}
