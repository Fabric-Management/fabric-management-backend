package com.fabricmanagement.common.infrastructure.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
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

  @JsonCreator
  public TenantSettingsUpdatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("timezone") String timezone,
      @JsonProperty("locale") String locale,
      @JsonProperty("currency") String currency) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TENANT_SETTINGS_UPDATED",
        occurredAt,
        correlationId);
    this.tenantId = tenantId;
    this.timezone = timezone;
    this.locale = locale;
    this.currency = currency;
  }
}
