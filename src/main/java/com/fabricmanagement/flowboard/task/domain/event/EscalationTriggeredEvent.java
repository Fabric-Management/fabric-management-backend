package com.fabricmanagement.flowboard.task.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Eskalasyon gerçekleştiğinde fırlatılır. NotificationHub bu eventi dinleyerek yöneticiye bildirim
 * gönderir.
 *
 * <p>[K4 FIX] DomainEvent extend edilerek event metadata (eventId, occurredAt) sağlanıyor.
 */
@Getter
public class EscalationTriggeredEvent extends DomainEvent {
  private final UUID taskId;
  private final String taskNumber;
  private final String escalationType;
  private final UUID escalatedToUserId;
  private final String message;

  public EscalationTriggeredEvent(
      UUID tenantId,
      UUID taskId,
      String taskNumber,
      String escalationType,
      UUID escalatedToUserId,
      String message) {
    super(tenantId, "ESCALATION_TRIGGERED");
    this.taskId = taskId;
    this.taskNumber = taskNumber;
    this.escalationType = escalationType;
    this.escalatedToUserId = escalatedToUserId;
    this.message = message;
  }
}
