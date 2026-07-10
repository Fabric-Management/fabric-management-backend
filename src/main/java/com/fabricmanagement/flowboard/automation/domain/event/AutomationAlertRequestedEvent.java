package com.fabricmanagement.flowboard.automation.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class AutomationAlertRequestedEvent extends DomainEvent {

  private final UUID boardId;
  private final UUID taskId;
  private final UUID recipientId; // nullable (null means notify manager/owner)
  private final String message;

  @JsonCreator
  public AutomationAlertRequestedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("boardId") UUID boardId,
      @JsonProperty("taskId") UUID taskId,
      @JsonProperty("recipientId") UUID recipientId,
      @JsonProperty("message") String message) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "AUTOMATION_ALERT_REQUESTED",
        occurredAt,
        correlationId);
    this.boardId = boardId;
    this.taskId = taskId;
    this.recipientId = recipientId;
    this.message = message;
  }

  private AutomationAlertRequestedEvent(
      UUID tenantId, UUID boardId, UUID taskId, UUID recipientId, String message) {
    super(tenantId, "AUTOMATION_ALERT_REQUESTED");
    this.boardId = boardId;
    this.taskId = taskId;
    this.recipientId = recipientId;
    this.message = message;
  }

  public static AutomationAlertRequestedEvent forManager(
      UUID tenantId, UUID boardId, UUID taskId, String message) {
    return new AutomationAlertRequestedEvent(tenantId, boardId, taskId, null, message);
  }

  public static AutomationAlertRequestedEvent forUser(
      UUID tenantId, UUID boardId, UUID taskId, UUID recipientId, String message) {
    return new AutomationAlertRequestedEvent(tenantId, boardId, taskId, recipientId, message);
  }
}
