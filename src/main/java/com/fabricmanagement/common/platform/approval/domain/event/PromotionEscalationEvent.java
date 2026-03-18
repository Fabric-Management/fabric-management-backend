package com.fabricmanagement.common.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
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
}
