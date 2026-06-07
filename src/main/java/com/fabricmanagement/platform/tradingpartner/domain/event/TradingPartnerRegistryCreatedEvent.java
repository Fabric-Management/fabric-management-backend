package com.fabricmanagement.platform.tradingpartner.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a new trading partner registry (golden record) is created.
 *
 * <p>This is a platform-level event — uses {@link TenantContext#SYSTEM_TENANT_ID} as sentinel since
 * registries are not tenant-scoped.
 */
@Getter
public class TradingPartnerRegistryCreatedEvent extends DomainEvent {

  private final UUID registryId;
  private final String taxId;
  private final String officialName;
  private final String country;

  public TradingPartnerRegistryCreatedEvent(
      UUID registryId, String taxId, String officialName, String country) {
    super(TenantContext.SYSTEM_TENANT_ID, "TRADING_PARTNER_REGISTRY_CREATED");
    this.registryId = registryId;
    this.taxId = taxId;
    this.officialName = officialName;
    this.country = country;
  }
}
