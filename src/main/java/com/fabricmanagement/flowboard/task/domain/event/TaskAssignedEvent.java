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
  public enum Origin {
    TASK_SERVICE,
    ROUTING_EVALUATION
  }

  private final UUID taskId;
  private final UUID assignmentId;
  private final UUID assignedUserId;
  private final UUID assignedByUserId;
  private final Origin origin;

  @JsonCreator
  public TaskAssignedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("taskId") UUID taskId,
      @JsonProperty("assignmentId") UUID assignmentId,
      @JsonProperty("assignedUserId") UUID assignedUserId,
      @JsonProperty("assignedByUserId") UUID assignedByUserId,
      @JsonProperty("origin") Origin origin) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TASK_ASSIGNED",
        occurredAt,
        correlationId);
    this.taskId = taskId;
    this.assignmentId = assignmentId;
    this.assignedUserId = assignedUserId;
    this.assignedByUserId = assignedByUserId;
    this.origin = origin != null ? origin : Origin.TASK_SERVICE;
  }

  public TaskAssignedEvent(
      UUID tenantId, UUID taskId, UUID assignmentId, UUID assignedUserId, UUID assignedByUserId) {
    this(tenantId, taskId, assignmentId, assignedUserId, assignedByUserId, Origin.TASK_SERVICE);
  }

  public TaskAssignedEvent(
      UUID tenantId,
      UUID taskId,
      UUID assignmentId,
      UUID assignedUserId,
      UUID assignedByUserId,
      Origin origin) {
    super(tenantId, "TASK_ASSIGNED");
    this.taskId = taskId;
    this.assignmentId = assignmentId;
    this.assignedUserId = assignedUserId;
    this.assignedByUserId = assignedByUserId;
    this.origin = origin != null ? origin : Origin.TASK_SERVICE;
  }

  /**
   * Deserialisation/source compatibility for publications created before assignment IDs existed.
   */
  public TaskAssignedEvent(UUID tenantId, UUID taskId, UUID assignedUserId, UUID assignedByUserId) {
    this(tenantId, taskId, null, assignedUserId, assignedByUserId);
  }
}
