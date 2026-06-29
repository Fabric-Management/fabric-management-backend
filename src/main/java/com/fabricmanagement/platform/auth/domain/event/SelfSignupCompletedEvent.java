package com.fabricmanagement.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/**
 * Event published when tenant onboarding completes enough data to send signup email after commit.
 */
@Getter
public class SelfSignupCompletedEvent extends DomainEvent {

  private final String recipientEmail;
  private final String firstName;
  private final String organizationName;
  private final String setupUrl;
  private final boolean salesLed;
  private final List<String> subscriptionOsCodes;
  private final String localeLanguageTag;

  public SelfSignupCompletedEvent(
      UUID tenantId,
      String recipientEmail,
      String firstName,
      String organizationName,
      String setupUrl,
      boolean salesLed,
      List<String> subscriptionOsCodes,
      String localeLanguageTag) {
    super(tenantId, "SELF_SIGNUP_COMPLETED");
    this.recipientEmail = recipientEmail;
    this.firstName = firstName;
    this.organizationName = organizationName;
    this.setupUrl = setupUrl;
    this.salesLed = salesLed;
    this.subscriptionOsCodes =
        subscriptionOsCodes == null ? List.of() : List.copyOf(subscriptionOsCodes);
    this.localeLanguageTag = localeLanguageTag;
  }
}
