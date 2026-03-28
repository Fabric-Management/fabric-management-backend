package com.fabricmanagement.flowboard.generator.app.adapter;

import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkOrderEventAdapter implements DomainEventAdapter<WorkOrderApprovedEvent> {

  @Override
  public Class<WorkOrderApprovedEvent> getSupportedEventType() {
    return WorkOrderApprovedEvent.class;
  }

  @Override
  public String getEventTypeName() {
    return "WorkOrderApproved";
  }

  @Override
  public TaskTemplateContext buildContext(WorkOrderApprovedEvent event) {
    return new TaskTemplateContext(
        event.getTenantId(),
        event.getWorkOrderId(),
        "WORK_ORDER",
        event.getWorkOrderNumber(),
        null, // Deadline WorkOrderApprovedEvent içerisinde yok
        Map.of()); // Interpolation değişkenleri şimdilik boş
  }
}
