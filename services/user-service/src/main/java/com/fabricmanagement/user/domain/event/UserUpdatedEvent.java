package com.fabricmanagement.user.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a user profile is updated.
 */
@Getter
@Builder
public class UserUpdatedEvent {
    
    private final UUID userId;
    private final UUID tenantId;
    private final UUID identityId;
    private final String firstName;
    private final String lastName;
    private final String displayName;
    private final String jobTitle;
    private final String department;
    private final LocalDateTime updatedAt;
    private final String eventType = "USER_UPDATED";
    
    public static UserUpdatedEvent of(UUID userId, UUID tenantId, UUID identityId,
                                     String firstName, String lastName, String displayName,
                                     String jobTitle, String department, LocalDateTime updatedAt) {
        return UserUpdatedEvent.builder()
                .userId(userId)
                .tenantId(tenantId)
                .identityId(identityId)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(displayName)
                .jobTitle(jobTitle)
                .department(department)
                .updatedAt(updatedAt)
                .build();
    }
}
