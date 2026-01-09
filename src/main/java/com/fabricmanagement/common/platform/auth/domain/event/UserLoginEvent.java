package com.fabricmanagement.common.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when user successfully logs in.
 *
 * <p>Listeners: Audit, Analytics, Monitoring (track user activity)
 */
@Getter
public class UserLoginEvent extends DomainEvent {

  private final UUID userId;
  private final String contactValue;
  private final String ipAddress;

  public UserLoginEvent(UUID tenantId, UUID userId, String contactValue, String ipAddress) {
    super(tenantId, "USER_LOGIN");
    this.userId = userId;
    this.contactValue = contactValue;
    this.ipAddress = ipAddress;
  }
}
