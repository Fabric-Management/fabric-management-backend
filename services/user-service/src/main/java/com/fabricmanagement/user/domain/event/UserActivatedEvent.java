package com.fabricmanagement.user.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a user profile is activated.
 */
@Getter
@Builder
public class UserActivatedEvent {
    
    private final UUID userId;
    private final UUID tenantId;
    private final UUID identityId;
    private final LocalDateTime activatedAt;
    private final String eventType = "USER_ACTIVATED";
    
    public static UserActivatedEvent of(UUID userId, UUID tenantId, UUID identityId, LocalDateTime activatedAt) {
        return UserActivatedEvent.builder()
                .userId(userId)
                .tenantId(tenantId)
                .identityId(identityId)
                .activatedAt(activatedAt)
                .build();
    }
}
