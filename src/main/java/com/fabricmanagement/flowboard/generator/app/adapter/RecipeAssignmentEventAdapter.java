package com.fabricmanagement.flowboard.generator.app.adapter;

import com.fabricmanagement.common.domain.event.production.WorkOrderRecipeAssignmentNeededEvent;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RecipeAssignmentEventAdapter
    implements DomainEventAdapter<WorkOrderRecipeAssignmentNeededEvent> {

  @Override
  public Class<WorkOrderRecipeAssignmentNeededEvent> getSupportedEventType() {
    return WorkOrderRecipeAssignmentNeededEvent.class;
  }

  @Override
  public String getEventTypeName() {
    return "WorkOrderRecipeAssignmentNeeded";
  }

  @Override
  public TaskTemplateContext buildContext(WorkOrderRecipeAssignmentNeededEvent event) {
    Map<String, String> vars = new HashMap<>();
    vars.put(
        "certificationReq",
        event.getCertificationReq() != null ? event.getCertificationReq() : "—");
    vars.put("originReq", event.getOriginReq() != null ? event.getOriginReq() : "—");
    vars.put(
        "salesOrderLineId",
        event.getSalesOrderLineId() != null ? event.getSalesOrderLineId().toString() : "—");

    return new TaskTemplateContext(
        event.getTenantId(),
        event.getWorkOrderId(), // entityId = WO id for idempotency guard
        "WORK_ORDER",
        event.getWorkOrderId().toString(), // entityRef
        null, // no deadline
        vars);
  }
}
