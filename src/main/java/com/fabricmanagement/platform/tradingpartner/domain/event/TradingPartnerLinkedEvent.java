package com.fabricmanagement.platform.tradingpartner.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a registry is linked to a platform tenant.
 *
 * <p>This is a platform-level event — uses {@link TenantContext#SYSTEM_TENANT_ID} as sentinel since
 * registry linking is cross-tenant.
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
    super(TenantContext.SYSTEM_TENANT_ID, "TRADING_PARTNER_LINKED");
    this.registryId = registryId;
    this.linkedTenantId = linkedTenantId;
    this.affectedTenantIds = affectedTenantIds;
  }

  @JsonCreator
  public TradingPartnerLinkedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("registryId") UUID registryId,
      @JsonProperty("linkedTenantId") UUID linkedTenantId,
      @JsonProperty("affectedTenantIds") List<UUID> affectedTenantIds) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TRADING_PARTNER_LINKED",
        occurredAt,
        correlationId);
    this.registryId = registryId;
    this.linkedTenantId = linkedTenantId;
    this.affectedTenantIds = affectedTenantIds;
  }
}
