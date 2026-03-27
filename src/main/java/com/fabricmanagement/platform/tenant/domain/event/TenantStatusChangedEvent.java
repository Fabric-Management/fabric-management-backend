package com.fabricmanagement.platform.tenant.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
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
}
