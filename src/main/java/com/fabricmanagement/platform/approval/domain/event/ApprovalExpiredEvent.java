package com.fabricmanagement.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** Zaman aşımına uğrayan onay talepleri iptal edildi — NORMAL. Hedef entity'lere bildirim. */
@Getter
public class ApprovalExpiredEvent extends DomainEvent {

  private final List<UUID> expiredRequestIds;
  private final int count;

  public ApprovalExpiredEvent(UUID tenantId, List<UUID> expiredRequestIds) {
    super(tenantId, "APPROVAL_EXPIRED");
    this.expiredRequestIds = expiredRequestIds;
    this.count = expiredRequestIds.size();
  }
}
