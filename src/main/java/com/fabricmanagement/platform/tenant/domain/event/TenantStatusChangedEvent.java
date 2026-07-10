package com.fabricmanagement.platform.tenant.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Domain event published when a tenant's status changes.
 *
 * <p>Listeners: Audit, Billing, Notification modules
 */
@Getter
public class TenantStatusChangedEvent extends DomainEvent {

  private final String uid;
  private final TenantStatus previousStatus;
  private final TenantStatus newStatus;
  private final String reason;

  public TenantStatusChangedEvent(
      UUID tenantId,
      String uid,
      TenantStatus previousStatus,
      TenantStatus newStatus,
      String reason) {
    super(tenantId, "TENANT_STATUS_CHANGED");
    this.uid = uid;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.reason = reason;
  }

  @JsonCreator
  public TenantStatusChangedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("uid") String uid,
      @JsonProperty("previousStatus") TenantStatus previousStatus,
      @JsonProperty("newStatus") TenantStatus newStatus,
      @JsonProperty("reason") String reason) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TENANT_STATUS_CHANGED",
        occurredAt,
        correlationId);
    this.uid = uid;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.reason = reason;
  }
}
