package com.fabricmanagement.flowboard.task.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
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

  @JsonCreator
  public TaskChecklistCompletedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("taskId") UUID taskId,
      @JsonProperty("boardId") UUID boardId,
      @JsonProperty("checklistId") UUID checklistId,
      @JsonProperty("completedByUserId") UUID completedByUserId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "CHECKLIST_COMPLETED",
        occurredAt,
        correlationId);
    this.taskId = taskId;
    this.boardId = boardId;
    this.checklistId = checklistId;
    this.completedByUserId = completedByUserId;
  }

  public TaskChecklistCompletedEvent(
      UUID tenantId, UUID taskId, UUID boardId, UUID checklistId, UUID completedByUserId) {
    super(tenantId, "CHECKLIST_COMPLETED");
    this.taskId = taskId;
    this.boardId = boardId;
    this.checklistId = checklistId;
    this.completedByUserId = completedByUserId;
  }
}
