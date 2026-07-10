package com.fabricmanagement.flowboard.task.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Task bir kullanıcıya atandığında fırlatılır. Faz 8.3'te doğrudan WebSocket gönderimi yerine
 * domain event pub-sub modeline geçildiği için kullanılır (B2/AUT5 teknik borcu).
 */
@Getter
public class TaskAssignedEvent extends DomainEvent {
  private final UUID taskId;
  private final UUID assignedUserId;
  private final UUID assignedByUserId;

  @JsonCreator
  public TaskAssignedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("taskId") UUID taskId,
      @JsonProperty("assignedUserId") UUID assignedUserId,
      @JsonProperty("assignedByUserId") UUID assignedByUserId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TASK_ASSIGNED",
        occurredAt,
        correlationId);
    this.taskId = taskId;
    this.assignedUserId = assignedUserId;
    this.assignedByUserId = assignedByUserId;
  }

  public TaskAssignedEvent(UUID tenantId, UUID taskId, UUID assignedUserId, UUID assignedByUserId) {
    super(tenantId, "TASK_ASSIGNED");
    this.taskId = taskId;
    this.assignedUserId = assignedUserId;
    this.assignedByUserId = assignedByUserId;
  }
}
