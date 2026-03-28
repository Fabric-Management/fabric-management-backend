package com.fabricmanagement.flowboard.common.exception;

import com.fabricmanagement.flowboard.task.domain.TaskStatus;

/**
 * Thrown when a task status transition is not permitted by the state machine.
 *
 * <h2>HTTP Response -- 409 Conflict</h2>
 */
public class TaskStatusTransitionException extends FlowBoardDomainException {

  public TaskStatusTransitionException(TaskStatus from, TaskStatus to) {
    super(
        String.format("Task status transition %s -> %s is not allowed", from, to),
        "TASK_INVALID_TRANSITION",
        409);
    withDetail("from", from.name());
    withDetail("to", to.name());
  }

  public TaskStatusTransitionException(TaskStatus from, TaskStatus to, String reason) {
    super(
        String.format("Task status transition %s -> %s is not allowed: %s", from, to, reason),
        "TASK_INVALID_TRANSITION",
        409);
    withDetail("from", from.name());
    withDetail("to", to.name());
    withDetail("reason", reason);
  }
}
