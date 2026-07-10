package com.fabricmanagement.platform.approval.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
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

  @JsonCreator
  public ApprovalExpiredEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("expiredRequestIds") List<UUID> expiredRequestIds,
      @JsonProperty("count") int count) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "APPROVAL_EXPIRED",
        occurredAt,
        correlationId);
    this.expiredRequestIds = expiredRequestIds != null ? List.copyOf(expiredRequestIds) : List.of();
    this.count = count;
  }
}
