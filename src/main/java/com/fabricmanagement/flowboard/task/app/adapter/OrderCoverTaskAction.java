package com.fabricmanagement.flowboard.task.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.*;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCommandPort;
import com.fabricmanagement.sales.salesorder.dto.ConfirmProductionCoverPayload;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCoverTaskAction implements DomainTaskAction {
  public static final String ACTION = "CONFIRM_PRODUCTION_COVER";
  private final OrderCoverAuthorisation authorisation;
  private final OrderCoverCommandPort orderCover;

  @Override
  public String actionKey() {
    return ACTION;
  }

  @Override
  public boolean supports(Task task) {
    return task.getTaskType() == TaskType.ORDER_COVER
        && TaskWorkflowRegistry.ORDER_COVER_DEFINITION_ID.equals(task.getWorkflowDefinitionId())
        && Integer.valueOf(1).equals(task.getWorkflowVersion());
  }

  @Override
  public DomainTaskActionResult execute(Task task, TaskActionCommand command) {
    if (!(command.payload() instanceof ConfirmProductionCoverPayload payload))
      throw new IllegalArgumentException("CONFIRM_PRODUCTION_COVER requires its typed payload");
    authorisation.assertFirstExecution(task, command.actorId());
    var decision =
        orderCover.confirm(
            TenantContext.requireTenantId(), task.getEntityId(), command.actorId(), payload);
    return switch (decision) {
      case OrderCoverCommandPort.Decision.Accepted accepted ->
          new DomainTaskActionResult.Accepted(
              "ORDER_COVER_RESULT",
              accepted.resultId(),
              accepted.remainingLineIds().stream()
                  .map(id -> new TaskSubject("SALES_ORDER_LINE", id))
                  .collect(Collectors.toSet()));
      case OrderCoverCommandPort.Decision.Rejected rejected ->
          new DomainTaskActionResult.Rejected("COVER_PRECONDITION_FAILED", rejected.code());
    };
  }
}
