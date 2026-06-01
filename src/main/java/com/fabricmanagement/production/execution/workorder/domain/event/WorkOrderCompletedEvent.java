package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Published when a WorkOrder finishes production and transitions to COMPLETED. */
@Getter
public class WorkOrderCompletedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID salesOrderLineId;
  private final String workOrderNumber;
  private final BigDecimal plannedQty;
  private final BigDecimal actualQty;
  private final BigDecimal totalConsumed;
  private final BigDecimal yieldPercentage;
  private final Instant completedAt;
  private final UUID completedBy;

  public WorkOrderCompletedEvent(
      UUID tenantId,
      UUID workOrderId,
      UUID salesOrderLineId,
      String workOrderNumber,
      BigDecimal plannedQty,
      BigDecimal actualQty,
      BigDecimal totalConsumed,
      BigDecimal yieldPercentage,
      Instant completedAt,
      UUID completedBy) {
    super(tenantId, "WORK_ORDER_COMPLETED");
    this.workOrderId = workOrderId;
    this.salesOrderLineId = salesOrderLineId;
    this.workOrderNumber = workOrderNumber;
    this.plannedQty = plannedQty;
    this.actualQty = actualQty;
    this.totalConsumed = totalConsumed;
    this.yieldPercentage = yieldPercentage;
    this.completedAt = completedAt;
    this.completedBy = completedBy;
  }
}
