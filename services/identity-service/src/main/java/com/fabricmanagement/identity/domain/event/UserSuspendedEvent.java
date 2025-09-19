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
public class UserSuspendedEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String suspensionReason;
    private String suspendedBy; // ADMIN, SYSTEM, etc.
    private LocalDateTime suspensionExpiresAt;
    private boolean isPermanent;
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "UserSuspended";
    }
}