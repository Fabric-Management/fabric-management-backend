package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** WorkOrder deadline atandı — FlowBoard PriorityScore günceller. Önem: NORMAL */
@Getter
public class WorkOrderDeadlineSetEvent extends DomainEvent {

  private final UUID workOrderId;
  private final String workOrderNumber;
  private final Instant deadline;

  public WorkOrderDeadlineSetEvent(
      UUID tenantId, UUID workOrderId, String workOrderNumber, Instant deadline) {
    super(tenantId, "WORK_ORDER_DEADLINE_SET");
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.deadline = deadline;
  }
}
