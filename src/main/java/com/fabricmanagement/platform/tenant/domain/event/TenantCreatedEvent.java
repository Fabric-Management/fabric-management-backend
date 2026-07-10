package com.fabricmanagement.platform.tenant.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Domain event published when a new tenant is created.
 *
 * <p>Listeners: Audit, Analytics, Notification modules
 */
@Getter
public class TenantCreatedEvent extends DomainEvent {

  private final String uid;
  private final String name;
  private final TenantStatus status;
  private final Instant trialEndsAt;

  public TenantCreatedEvent(
      UUID tenantId, String uid, String name, TenantStatus status, Instant trialEndsAt) {
    super(tenantId, "TENANT_CREATED");
    this.uid = uid;
    this.name = name;
    this.status = status;
    this.trialEndsAt = trialEndsAt;
  }

  @JsonCreator
  public TenantCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("uid") String uid,
      @JsonProperty("name") String name,
      @JsonProperty("status") TenantStatus status,
      @JsonProperty("trialEndsAt") Instant trialEndsAt) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TENANT_CREATED",
        occurredAt,
        correlationId);
    this.uid = uid;
    this.name = name;
    this.status = status;
    this.trialEndsAt = trialEndsAt;
  }
}
