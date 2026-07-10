package com.fabricmanagement.platform.tradingpartner.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a trading partner's status changes (suspend, block, reactivate, delete).
 *
 * <p>Listeners can use this event to:
 *
 * <ul>
 *   <li>Update audit logs
 *   <li>Send notifications to affected users
 *   <li>Cascade status changes to related records (orders, invoices)
 *   <li>Trigger compliance workflows
 * </ul>
 */
@Getter
public class TradingPartnerStatusChangedEvent extends DomainEvent {

  private final UUID tradingPartnerId;
  private final String previousStatus;
  private final String newStatus;
  private final String partnerDisplayName;

  public TradingPartnerStatusChangedEvent(
      UUID tenantId,
      UUID tradingPartnerId,
      String previousStatus,
      String newStatus,
      String partnerDisplayName) {
    super(tenantId, "TRADING_PARTNER_STATUS_CHANGED");
    this.tradingPartnerId = tradingPartnerId;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.partnerDisplayName = partnerDisplayName;
  }

  @JsonCreator
  public TradingPartnerStatusChangedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("tradingPartnerId") UUID tradingPartnerId,
      @JsonProperty("previousStatus") String previousStatus,
      @JsonProperty("newStatus") String newStatus,
      @JsonProperty("partnerDisplayName") String partnerDisplayName) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "TRADING_PARTNER_STATUS_CHANGED",
        occurredAt,
        correlationId);
    this.tradingPartnerId = tradingPartnerId;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.partnerDisplayName = partnerDisplayName;
  }
}
