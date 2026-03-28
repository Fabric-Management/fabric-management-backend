package com.fabricmanagement.flowboard.task.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

/** Bir task'ın checklist'indeki bir madde tamamlandığında tetiklenir (Faz 3.1). */
@Getter
@ToString(callSuper = true)
public class TaskChecklistCompletedEvent extends DomainEvent {

  private final UUID taskId;
  private final UUID boardId;
  private final UUID checklistId;
  private final UUID completedByUserId;

  public TaskChecklistCompletedEvent(
      UUID tenantId, UUID taskId, UUID boardId, UUID checklistId, UUID completedByUserId) {
    super(tenantId, "CHECKLIST_COMPLETED");
    this.taskId = taskId;
    this.boardId = boardId;
    this.checklistId = checklistId;
    this.completedByUserId = completedByUserId;
  }
}
