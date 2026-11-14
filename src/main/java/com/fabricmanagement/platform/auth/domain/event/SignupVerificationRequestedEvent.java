package com.fabricmanagement.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A self-service signup form was accepted: mail the setup link that proves the e-mail address.
 *
 * <p>No tenant exists yet (SIGNUP-DEFER-1), so the event carries the platform tenant id; the mail
 * is a platform mail, not a tenant mail.
 */
@Getter
public class SignupVerificationRequestedEvent extends DomainEvent {

  private final String recipientEmail;
  private final String firstName;
  private final String organizationName;
  private final String setupUrl;
  private final String localeLanguageTag;

  public SignupVerificationRequestedEvent(
      UUID platformTenantId,
      String recipientEmail,
      String firstName,
      String organizationName,
      String setupUrl,
      String localeLanguageTag) {
    super(platformTenantId, "SIGNUP_VERIFICATION_REQUESTED");
    this.recipientEmail = recipientEmail;
    this.firstName = firstName;
    this.organizationName = organizationName;
    this.setupUrl = setupUrl;
    this.localeLanguageTag = localeLanguageTag;
  }

  @JsonCreator
  public SignupVerificationRequestedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("recipientEmail") String recipientEmail,
      @JsonProperty("firstName") String firstName,
      @JsonProperty("organizationName") String organizationName,
      @JsonProperty("setupUrl") String setupUrl,
      @JsonProperty("localeLanguageTag") String localeLanguageTag) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SIGNUP_VERIFICATION_REQUESTED",
        occurredAt,
        correlationId);
    this.recipientEmail = recipientEmail;
    this.firstName = firstName;
    this.organizationName = organizationName;
    this.setupUrl = setupUrl;
    this.localeLanguageTag = localeLanguageTag;
  }
}
