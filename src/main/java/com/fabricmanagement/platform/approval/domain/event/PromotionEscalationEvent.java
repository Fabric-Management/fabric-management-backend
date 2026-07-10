package com.fabricmanagement.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Kullanıcı hesabı 3 defa terfi reddi sonucunda askıya alındı — CRITICAL. HR departmanına acil
 * bildirim hedefli event.
 */
@Getter
public class PromotionEscalationEvent extends DomainEvent {

  private final UUID userId;
  private final int rejectionCount;
  private final String reason;

  public PromotionEscalationEvent(UUID tenantId, UUID userId, int rejectionCount, String reason) {
    super(tenantId, "PROMOTION_ESCALATION");
    this.userId = userId;
    this.rejectionCount = rejectionCount;
    this.reason = reason;
  }

  @JsonCreator
  public PromotionEscalationEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("rejectionCount") int rejectionCount,
      @JsonProperty("reason") String reason) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PROMOTION_ESCALATION",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.rejectionCount = rejectionCount;
    this.reason = reason;
  }
}
