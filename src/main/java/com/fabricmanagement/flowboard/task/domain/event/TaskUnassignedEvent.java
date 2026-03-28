package com.fabricmanagement.flowboard.task.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** Task'tan bir kullanıcının ataması kaldırıldığında fırlatılır. */
@Getter
public class TaskUnassignedEvent extends DomainEvent {
  private final UUID taskId;
  private final UUID unassignedUserId;
  private final UUID unassignedByUserId;

  public TaskUnassignedEvent(
      UUID tenantId, UUID taskId, UUID unassignedUserId, UUID unassignedByUserId) {
    super(tenantId, "TASK_UNASSIGNED");
    this.taskId = taskId;
    this.unassignedUserId = unassignedUserId;
    this.unassignedByUserId = unassignedByUserId;
  }
}
