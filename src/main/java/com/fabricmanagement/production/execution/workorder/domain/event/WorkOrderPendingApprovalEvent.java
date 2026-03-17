package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * WorkOrder onay bekliyor — FlowBoard'a APPROVAL görevi düşer, yöneticiye bildirim gider. Önem:
 * HIGH
 */
@Getter
public class WorkOrderPendingApprovalEvent extends DomainEvent {

  private final UUID workOrderId;
  private final String workOrderNumber;
  private final UUID assignedToUserId; // onaylaması gereken kişi

  public WorkOrderPendingApprovalEvent(
      UUID tenantId, UUID workOrderId, String workOrderNumber, UUID assignedToUserId) {
    super(tenantId, "WORK_ORDER_PENDING_APPROVAL");
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.assignedToUserId = assignedToUserId;
  }
}
