package com.fabricmanagement.common.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when user profile is updated.
 *
 * <p>Listeners: Audit, Analytics, Notifications
 */
@Getter
public class UserProfileUpdatedEvent extends DomainEvent {

  private final UUID userId;
  private final UUID updatedBy;
  private final Set<ProfileCategory> categories;

  public UserProfileUpdatedEvent(
      UUID tenantId, UUID userId, UUID updatedBy, Set<ProfileCategory> categories) {
    super(tenantId, "USER_PROFILE_UPDATED");
    this.userId = userId;
    this.updatedBy = updatedBy;
    this.categories = categories;
  }
}
