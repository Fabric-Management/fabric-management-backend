package com.fabricmanagement.user.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a user profile is deactivated.
 */
@Getter
@Builder
public class UserDeactivatedEvent {
    
    private final UUID userId;
    private final UUID tenantId;
    private final UUID identityId;
    private final LocalDateTime deactivatedAt;
    private final String eventType = "USER_DEACTIVATED";
    
    public static UserDeactivatedEvent of(UUID userId, UUID tenantId, UUID identityId, LocalDateTime deactivatedAt) {
        return UserDeactivatedEvent.builder()
                .userId(userId)
                .tenantId(tenantId)
                .identityId(identityId)
                .deactivatedAt(deactivatedAt)
                .build();
    }
}
