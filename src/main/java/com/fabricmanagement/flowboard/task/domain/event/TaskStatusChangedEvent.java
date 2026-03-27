package com.fabricmanagement.flowboard.task.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a Task's status changes within FlowBoard.
 *
 * <p>[EV1 FIX] Implemented as a proper domain event.
 *
 * <p>Listeners:
 *
 * <ul>
 *   <li>{@link com.fabricmanagement.flowboard.task.app.TaskEventListener} — WS broadcast (B2 fix)
 *   <li>{@link com.fabricmanagement.flowboard.automation.app.AutomationEngine} — rule triggering
 * </ul>
 */
@Getter
public class TaskStatusChangedEvent extends DomainEvent {

  private final UUID taskId;
  private final UUID boardId;
  private final String oldStatus;
  private final String newStatus;
  private final UUID changedByUserId;

  public TaskStatusChangedEvent(
      UUID tenantId,
      UUID taskId,
      UUID boardId,
      String oldStatus,
      String newStatus,
      UUID changedByUserId) {
    super(tenantId, "TASK_STATUS_CHANGED");
    this.taskId = taskId;
    this.boardId = boardId;
    this.oldStatus = oldStatus;
    this.newStatus = newStatus;
    this.changedByUserId = changedByUserId;
  }
}
