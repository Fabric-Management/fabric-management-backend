package com.fabricmanagement.flowboard.task.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.DomainTaskAction;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCommandPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelOrderCoverTaskAction implements DomainTaskAction {
  public static final String ACTION = "CANCEL_ORDER_COVER";
  private final OrderCoverCommandPort orderCover;

  public String actionKey() {
    return ACTION;
  }

  public boolean supports(Task task) {
    return task.getTaskType() == TaskType.ORDER_COVER
        && com.fabricmanagement.flowboard.task.app.TaskWorkflowRegistry.ORDER_COVER_DEFINITION_ID
            .equals(task.getWorkflowDefinitionId())
        && Integer.valueOf(1).equals(task.getWorkflowVersion());
  }

  public DomainTaskActionResult execute(Task task, TaskActionCommand command) {
    if (!(command.payload() instanceof CancelOrderCoverPayload))
      throw new IllegalArgumentException("Cancellation payload required");
    return new DomainTaskActionResult.Accepted(
        "ORDER_COVER_CANCELLATION",
        orderCover.cancel(TenantContext.requireTenantId(), task.getEntityId()),
        java.util.Set.of());
  }
}
