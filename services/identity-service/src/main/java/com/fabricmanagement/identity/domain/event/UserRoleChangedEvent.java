package com.fabricmanagement.identity.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleChangedEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String previousRole;
    private String newRole;
    private String changedBy; // ADMIN, SYSTEM, etc.
    private String reason; // PROMOTION, DEMOTION, TRANSFER, etc.
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "UserRoleChanged";
    }
}