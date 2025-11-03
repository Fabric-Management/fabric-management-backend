package com.fabricmanagement.common.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event published when a profile update request is created.
 */
@Getter
public class ProfileUpdateRequestCreatedEvent extends DomainEvent {

    private final UUID requestId;
    private final UUID userId;
    private final String profileCategory;

    public ProfileUpdateRequestCreatedEvent(UUID tenantId, UUID requestId, UUID userId, String profileCategory) {
        super(tenantId, "PROFILE_UPDATE_REQUEST_CREATED");
        this.requestId = requestId;
        this.userId = userId;
        this.profileCategory = profileCategory;
    }
}

