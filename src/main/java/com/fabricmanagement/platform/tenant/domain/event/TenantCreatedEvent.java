package com.fabricmanagement.platform.tenant.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
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
}
