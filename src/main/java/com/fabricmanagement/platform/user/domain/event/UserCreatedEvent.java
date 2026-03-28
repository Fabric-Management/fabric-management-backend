package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a new user is created.
 *
 * <p>Listeners: Audit, Analytics, Notification, Policy (cache warm-up)
 */
@Getter
public class UserCreatedEvent extends DomainEvent {

  private final UUID userId;
  private final String displayName;
  private final String contactValue;
  private final UUID organizationId;

  public UserCreatedEvent(
      UUID tenantId, UUID userId, String displayName, String contactValue, UUID organizationId) {
    super(tenantId, "USER_CREATED");
    this.userId = userId;
    this.displayName = displayName;
    this.contactValue = contactValue;
    this.organizationId = organizationId;
  }
}
