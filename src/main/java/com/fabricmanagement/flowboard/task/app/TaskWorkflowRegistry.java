package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.domain.PinnedWorkflow;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves code-defined immutable workflow versions for newly provisioned tasks. */
@Component
public class TaskWorkflowRegistry {

  public static final UUID LEGACY_DEFINITION_ID =
      UUID.fromString("a9ce2df6-f274-4f61-8e53-d28f27472b01");
  public static final UUID ORDER_COVER_DEFINITION_ID =
      UUID.fromString("776c3f45-9025-40bc-b3b1-e027800c0246");

  public PinnedWorkflow latestFor(TaskType taskType) {
    if (taskType == null) {
      throw new IllegalArgumentException("Task type is required to resolve a workflow");
    }
    return new PinnedWorkflow(
        taskType == TaskType.ORDER_COVER ? ORDER_COVER_DEFINITION_ID : LEGACY_DEFINITION_ID, 1);
  }

  public PinnedWorkflow resolve(UUID definitionId, Integer version) {
    if (definitionId == null || version == null) {
      throw unsupportedPin(definitionId, version);
    }
    if ((LEGACY_DEFINITION_ID.equals(definitionId)
            || ORDER_COVER_DEFINITION_ID.equals(definitionId))
        && version == 1) {
      return new PinnedWorkflow(definitionId, version);
    }
    throw unsupportedPin(definitionId, version);
  }

  public boolean isGoverned(UUID definitionId, Integer version) {
    return ORDER_COVER_DEFINITION_ID.equals(definitionId) && Integer.valueOf(1).equals(version);
  }

  public boolean allowsManualCreation(TaskType taskType) {
    PinnedWorkflow workflow = latestFor(taskType);
    return !isGoverned(workflow.definitionId(), workflow.version());
  }

  private static FlowBoardDomainException unsupportedPin(UUID definitionId, Integer version) {
    return new FlowBoardDomainException(
        "Task has no supported workflow definition pin",
        "FLOWBOARD_WORKFLOW_PIN_UNSUPPORTED",
        409,
        new Object[] {definitionId, version});
  }
}
