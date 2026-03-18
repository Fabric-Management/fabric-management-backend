package com.fabricmanagement.flowboard.task.app.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * FlowBoard içinde bir Task'ın statüsü değiştiğinde yayınlanır.
 *
 * <p>[EV1 FIX] Domain event olarak implemente edildi.
 *
 * <p>Dinleyenler:
 *
 * <ul>
 *   <li>{@link com.fabricmanagement.flowboard.task.app.TaskEventListener} — WS yayın (B2 fix)
 *   <li>{@link com.fabricmanagement.flowboard.automation.app.AutomationEngine} — kural tetikleme
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
