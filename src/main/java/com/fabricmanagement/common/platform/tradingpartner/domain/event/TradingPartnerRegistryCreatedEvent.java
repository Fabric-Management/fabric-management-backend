package com.fabricmanagement.common.platform.tradingpartner.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a new trading partner registry (golden record) is created.
 *
 * <p>This is a platform-level event (tenantId is null) for audit and tracking purposes.
 */
@Getter
public class TradingPartnerRegistryCreatedEvent extends DomainEvent {

  private final UUID registryId;
  private final String taxId;
  private final String officialName;
  private final String country;

  public TradingPartnerRegistryCreatedEvent(
      UUID registryId, String taxId, String officialName, String country) {
    super(null, "TRADING_PARTNER_REGISTRY_CREATED"); // Platform-level event
    this.registryId = registryId;
    this.taxId = taxId;
    this.officialName = officialName;
    this.country = country;
  }
}
