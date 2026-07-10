package com.fabricmanagement.platform.tradingpartner.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a new trading partner relationship is created.
 *
 * <p>Listeners can use this event to:
 *
 * <ul>
 *   <li>Update audit logs
 *   <li>Send notifications
 *   <li>Initialize partner-specific configurations
 *   <li>Trigger integration workflows
 * </ul>
 */
@Getter
public class TradingPartnerCreatedEvent extends DomainEvent {

  private final UUID tradingPartnerId;
  private final UUID registryId;
  private final String partnerType;
  private final String displayName;
  private final UUID legacyCompanyId;

  public TradingPartnerCreatedEvent(
      UUID tenantId,
      UUID tradingPartnerId,
      UUID registryId,
      String partnerType,
      String displayName,
      UUID legacyCompanyId) {
    super(tenantId, "TRADING_PARTNER_CREATED");
    this.tradingPartnerId = tradingPartnerId;
    this.registryId = registryId;
    this.partnerType = partnerType;
    this.displayName = displayName;
    this.legacyCompanyId = legacyCompanyId;
  }

  @JsonCreator
  public TradingPartnerCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("tradingPartnerId") UUID tradingPartnerId,
      @JsonProperty("registryId") UUID registryId,
      @JsonProperty("partnerType") String partnerType,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("legacyCompanyId") UUID legacyCompanyId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TRADING_PARTNER_CREATED",
        occurredAt,
        correlationId);
    this.tradingPartnerId = tradingPartnerId;
    this.registryId = registryId;
    this.partnerType = partnerType;
    this.displayName = displayName;
    this.legacyCompanyId = legacyCompanyId;
  }
}
