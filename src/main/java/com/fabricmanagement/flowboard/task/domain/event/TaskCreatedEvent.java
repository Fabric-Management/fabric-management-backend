package com.fabricmanagement.flowboard.task.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a new Task is created within FlowBoard.
 *
 * <p>[EV1 FIX] Implemented as a proper domain event.
 *
 * <p>Listeners:
 *
 * <ul>
 *   <li>{@link com.fabricmanagement.flowboard.task.app.TaskEventListener} — WS broadcast (B2 fix)
 *   <li>{@link com.fabricmanagement.flowboard.automation.app.AutomationEngine} — rule evaluation
 * </ul>
 */
@Getter
public class TaskCreatedEvent extends DomainEvent {

  private final UUID taskId;
  private final UUID boardId;
  private final String taskNumber;
  private final String status;
  private final String taskType;

  public TaskCreatedEvent(
      UUID tenantId, UUID taskId, UUID boardId, String taskNumber, String status, String taskType) {
    super(tenantId, "TASK_CREATED");
    this.taskId = taskId;
    this.boardId = boardId;
    this.taskNumber = taskNumber;
    this.status = status;
    this.taskType = taskType;
  }
}
