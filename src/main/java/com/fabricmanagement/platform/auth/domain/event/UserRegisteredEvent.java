package com.fabricmanagement.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when user completes registration.
 *
 * <p>Listeners: Audit, Analytics, Notification (welcome email)
 */
@Getter
public class UserRegisteredEvent extends DomainEvent {

  private final UUID userId;
  private final String contactValue;

  public UserRegisteredEvent(UUID tenantId, UUID userId, String contactValue) {
    super(tenantId, "USER_REGISTERED");
    this.userId = userId;
    this.contactValue = contactValue;
  }
}
