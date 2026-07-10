package com.fabricmanagement.platform.tradingpartner.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a new partner portal user is invited.
 *
 * <p>Distinct from {@link com.fabricmanagement.platform.user.domain.event.UserCreatedEvent} so the
 * partner-specific invitation listener can handle it separately (different email template,
 * different setup URL).
 *
 * <p>Listeners: {@link
 * com.fabricmanagement.platform.tradingpartner.app.PartnerUserInvitationEventListener}
 */
@Getter
public class PartnerUserCreatedEvent extends DomainEvent {

  private final UUID userId;
  private final String displayName;
  private final String contactValue;
  private final UUID partnerId;
  private final UUID organizationId;
  private final String partnerDisplayName;

  public PartnerUserCreatedEvent(
      UUID tenantId,
      UUID userId,
      String displayName,
      String contactValue,
      UUID partnerId,
      UUID organizationId,
      String partnerDisplayName) {
    super(tenantId, "PARTNER_USER_CREATED");
    this.userId = userId;
    this.displayName = displayName;
    this.contactValue = contactValue;
    this.partnerId = partnerId;
    this.organizationId = organizationId;
    this.partnerDisplayName = partnerDisplayName;
  }

  @JsonCreator
  public PartnerUserCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("contactValue") String contactValue,
      @JsonProperty("partnerId") UUID partnerId,
      @JsonProperty("organizationId") UUID organizationId,
      @JsonProperty("partnerDisplayName") String partnerDisplayName) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PARTNER_USER_CREATED",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.displayName = displayName;
    this.contactValue = contactValue;
    this.partnerId = partnerId;
    this.organizationId = organizationId;
    this.partnerDisplayName = partnerDisplayName;
  }
}
