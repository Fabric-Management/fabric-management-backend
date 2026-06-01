package com.fabricmanagement.common.domain.event.production;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** Published after a linked WorkOrder transitions to IN_PROGRESS. */
@Getter
public class WorkOrderStartedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID salesOrderLineId;

  public WorkOrderStartedEvent(UUID tenantId, UUID workOrderId, UUID salesOrderLineId) {
    super(tenantId, "WORK_ORDER_STARTED");
    this.workOrderId = workOrderId;
    this.salesOrderLineId = salesOrderLineId;
  }
}
