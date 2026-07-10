package com.fabricmanagement.flowboard.task.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Task'tan bir kullanıcının ataması kaldırıldığında fırlatılır. */
@Getter
public class TaskUnassignedEvent extends DomainEvent {
  private final UUID taskId;
  private final UUID unassignedUserId;
  private final UUID unassignedByUserId;

  @JsonCreator
  public TaskUnassignedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("taskId") UUID taskId,
      @JsonProperty("unassignedUserId") UUID unassignedUserId,
      @JsonProperty("unassignedByUserId") UUID unassignedByUserId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TASK_UNASSIGNED",
        occurredAt,
        correlationId);
    this.taskId = taskId;
    this.unassignedUserId = unassignedUserId;
    this.unassignedByUserId = unassignedByUserId;
  }

  public TaskUnassignedEvent(
      UUID tenantId, UUID taskId, UUID unassignedUserId, UUID unassignedByUserId) {
    super(tenantId, "TASK_UNASSIGNED");
    this.taskId = taskId;
    this.unassignedUserId = unassignedUserId;
    this.unassignedByUserId = unassignedByUserId;
  }
}
