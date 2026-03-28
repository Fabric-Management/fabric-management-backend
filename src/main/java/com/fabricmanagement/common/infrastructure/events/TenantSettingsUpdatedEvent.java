package com.fabricmanagement.common.infrastructure.events;

import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a tenant's settings (specifically localization like timezone, currency, locale)
 * are updated. Placed in common/infrastructure/events to avoid domain modules depending on the
 * platform module, respecting architectural boundaries.
 */
@Getter
public class TenantSettingsUpdatedEvent extends DomainEvent {

  private final UUID tenantId;
  private final String timezone;
  private final String locale;
  private final String currency;

  public TenantSettingsUpdatedEvent(
      UUID tenantId, String timezone, String locale, String currency) {
    super(tenantId, "TENANT_SETTINGS_UPDATED");
    this.tenantId = tenantId;
    this.timezone = timezone;
    this.locale = locale;
    this.currency = currency;
  }
}
