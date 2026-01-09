package com.fabricmanagement.common.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a user completes onboarding.
 *
 * <p>Listeners: Analytics, Notifications, Audit
 */
@Getter
public class UserOnboardingCompletedEvent extends DomainEvent {

  private final UUID userId;

  public UserOnboardingCompletedEvent(UUID tenantId, UUID userId) {
    super(tenantId, "USER_ONBOARDING_COMPLETED");
    this.userId = userId;
  }
}
