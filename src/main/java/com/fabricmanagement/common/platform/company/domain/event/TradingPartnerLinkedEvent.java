package com.fabricmanagement.common.platform.company.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a registry is linked to a platform tenant.
 *
 * <p>This is a platform-level event (tenantId is null) that notifies all tenants with this registry
 * that the partner is now on the platform.
 *
 * <p>Listeners can use this event to:
 *
 * <ul>
 *   <li>Update UI to show "partner is on platform" badge
 *   <li>Enable cross-tenant features (order visibility, shared documents)
 *   <li>Send notifications to affected tenants
 *   <li>Update partner status to VERIFIED
 * </ul>
 */
@Getter
public class TradingPartnerLinkedEvent extends DomainEvent {

  private final UUID registryId;
  private final UUID linkedTenantId;
  private final List<UUID> affectedTenantIds;

  public TradingPartnerLinkedEvent(
      UUID registryId, UUID linkedTenantId, List<UUID> affectedTenantIds) {
    super(null, "TRADING_PARTNER_LINKED"); // Platform-level event
    this.registryId = registryId;
    this.linkedTenantId = linkedTenantId;
    this.affectedTenantIds = affectedTenantIds;
  }
}
