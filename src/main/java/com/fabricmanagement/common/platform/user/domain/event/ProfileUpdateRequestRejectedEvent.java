package com.fabricmanagement.common.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event published when a profile update request is rejected.
 */
@Getter
public class ProfileUpdateRequestRejectedEvent extends DomainEvent {

    private final UUID requestId;
    private final UUID userId;
    private final UUID reviewedBy;
    private final String profileCategory;
    private final String reviewComment;

    public ProfileUpdateRequestRejectedEvent(UUID tenantId, UUID requestId, UUID userId, UUID reviewedBy, String profileCategory, String reviewComment) {
        super(tenantId, "PROFILE_UPDATE_REQUEST_REJECTED");
        this.requestId = requestId;
        this.userId = userId;
        this.reviewedBy = reviewedBy;
        this.profileCategory = profileCategory;
        this.reviewComment = reviewComment;
    }
}

