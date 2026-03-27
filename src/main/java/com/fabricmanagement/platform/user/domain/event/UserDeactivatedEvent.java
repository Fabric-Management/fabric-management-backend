package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a user is deactivated.
 *
 * <p>CRITICAL: Must invalidate policy cache and revoke active sessions!
 *
 * <p>Listeners: Policy (invalidate cache), Auth (revoke tokens), Audit
 */
@Getter
public class UserDeactivatedEvent extends DomainEvent {

  private final UUID userId;
  private final String reason;

  public UserDeactivatedEvent(UUID tenantId, UUID userId, String reason) {
    super(tenantId, "USER_DEACTIVATED");
    this.userId = userId;
    this.reason = reason;
  }
}
