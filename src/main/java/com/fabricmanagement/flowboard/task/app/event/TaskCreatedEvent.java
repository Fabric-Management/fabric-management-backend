package com.fabricmanagement.flowboard.task.app.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * FlowBoard içinde yeni bir Task oluşturulduğunda yayınlanır.
 *
 * <p>[EV1 FIX] Domain event olarak implemente edildi.
 *
 * <p>Dinleyenler:
 *
 * <ul>
 *   <li>{@link com.fabricmanagement.flowboard.task.app.TaskEventListener} — WS yayın (B2 fix)
 *   <li>{@link com.fabricmanagement.flowboard.automation.app.AutomationEngine} — kural
 *       değerlendirme
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
