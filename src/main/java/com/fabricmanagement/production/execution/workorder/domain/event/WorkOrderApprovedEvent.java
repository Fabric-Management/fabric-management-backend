package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** WorkOrder onaylandı — üretim başlayabilir. Önem: NORMAL */
@Getter
public class WorkOrderApprovedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final String workOrderNumber;
  private final UUID approvedByUserId;

  public WorkOrderApprovedEvent(
      UUID tenantId, UUID workOrderId, String workOrderNumber, UUID approvedByUserId) {
    super(tenantId, "WORK_ORDER_APPROVED");
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.approvedByUserId = approvedByUserId;
  }
}
