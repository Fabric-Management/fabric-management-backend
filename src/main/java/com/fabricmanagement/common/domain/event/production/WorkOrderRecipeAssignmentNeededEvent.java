package com.fabricmanagement.common.domain.event.production;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Published by the rule engine when a sales order line requires production but no recipe can be
 * found (step-4 fallback). This event triggers manual recipe assignment in FlowBoard.
 */
@Getter
public class WorkOrderRecipeAssignmentNeededEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID salesOrderLineId;
  private final String certificationReq;
  private final String originReq;

  public WorkOrderRecipeAssignmentNeededEvent(
      UUID tenantId,
      UUID workOrderId,
      UUID salesOrderLineId,
      String certificationReq,
      String originReq) {
    super(tenantId, "WORK_ORDER_RECIPE_ASSIGNMENT_NEEDED");
    this.workOrderId = workOrderId;
    this.salesOrderLineId = salesOrderLineId;
    this.certificationReq = certificationReq;
    this.originReq = originReq;
  }
}
