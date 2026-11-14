package com.fabricmanagement.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * The signup form was sent with an e-mail address that already has an account. Nothing is created;
 * the person is told by mail to sign in and add the company under "My organisations". The signup
 * form itself answers exactly as for a new address, so the answer never reveals who is a customer.
 */
@Getter
public class ExistingAccountSignupAttemptedEvent extends DomainEvent {

  private final String recipientEmail;
  private final String firstName;
  private final String signInUrl;
  private final String localeLanguageTag;

  public ExistingAccountSignupAttemptedEvent(
      UUID platformTenantId,
      String recipientEmail,
      String firstName,
      String signInUrl,
      String localeLanguageTag) {
    super(platformTenantId, "SIGNUP_EXISTING_ACCOUNT");
    this.recipientEmail = recipientEmail;
    this.firstName = firstName;
    this.signInUrl = signInUrl;
    this.localeLanguageTag = localeLanguageTag;
  }

  @JsonCreator
  public ExistingAccountSignupAttemptedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("recipientEmail") String recipientEmail,
      @JsonProperty("firstName") String firstName,
      @JsonProperty("signInUrl") String signInUrl,
      @JsonProperty("localeLanguageTag") String localeLanguageTag) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SIGNUP_EXISTING_ACCOUNT",
        occurredAt,
        correlationId);
    this.recipientEmail = recipientEmail;
    this.firstName = firstName;
    this.signInUrl = signInUrl;
    this.localeLanguageTag = localeLanguageTag;
  }
}
