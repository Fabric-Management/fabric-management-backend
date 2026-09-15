package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskWorkflowPinTest {

  @Test
  void activeExecutionCannotChangeItsWorkflowPin() {
    Task task =
        Task.create(
            "TSK-0001",
            UUID.randomUUID(),
            "Governed task",
            TaskType.PLANNING,
            ModuleType.GENERAL,
            Priority.MEDIUM,
            null,
            null,
            "SALES_ORDER",
            UUID.randomUUID());
    UUID definitionId = UUID.randomUUID();
    task.govern("task:key", definitionId, 1);

    assertThatThrownBy(() -> task.govern("task:key", UUID.randomUUID(), 2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("immutable");
    assertThat(task.getWorkflowDefinitionId()).isEqualTo(definitionId);
    assertThat(task.getWorkflowVersion()).isEqualTo(1);
  }

  @Test
  void missingLegacyPinProducesTypedDomainRefusalInsteadOfUnboxingFailure() {
    assertThatThrownBy(() -> new TaskWorkflowRegistry().resolve(null, null))
        .isInstanceOf(
            com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException.class)
        .hasMessageContaining("workflow definition pin");
  }

  @Test
  void terminalExecutionCannotBeClosedAgain() {
    Task task =
        Task.create(
            "TSK-0002",
            UUID.randomUUID(),
            "Governed task",
            TaskType.ORDER_COVER,
            ModuleType.GENERAL,
            Priority.MEDIUM,
            null,
            null,
            "SALES_ORDER",
            UUID.randomUUID());
    task.govern("task:key", TaskWorkflowRegistry.ORDER_COVER_DEFINITION_ID, 1);
    task.cancel();

    assertThatThrownBy(() -> task.closeGovernedExecution(Instant.now()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be closed again");
  }
}
