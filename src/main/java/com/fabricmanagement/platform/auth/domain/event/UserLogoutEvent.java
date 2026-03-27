package com.fabricmanagement.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when user logs out.
 *
 * <p>Listeners: Audit, Analytics, Monitoring (track session end)
 */
@Getter
public class UserLogoutEvent extends DomainEvent {

  private final UUID userId;

  public UserLogoutEvent(UUID tenantId, UUID userId) {
    super(tenantId, "USER_LOGOUT");
    this.userId = userId;
  }
}
