package com.fabricmanagement.user.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a user profile is suspended.
 */
@Getter
@Builder
public class UserSuspendedEvent {
    
    private final UUID userId;
    private final UUID tenantId;
    private final UUID identityId;
    private final LocalDateTime suspendedAt;
    private final String eventType = "USER_SUSPENDED";
    
    public static UserSuspendedEvent of(UUID userId, UUID tenantId, UUID identityId, LocalDateTime suspendedAt) {
        return UserSuspendedEvent.builder()
                .userId(userId)
                .tenantId(tenantId)
                .identityId(identityId)
                .suspendedAt(suspendedAt)
                .build();
    }
}
