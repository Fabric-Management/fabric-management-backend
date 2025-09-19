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
public class UserLockedEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String lockReason; // FAILED_ATTEMPTS, SECURITY_VIOLATION, ADMIN_ACTION, etc.
    private String lockedBy; // SYSTEM, ADMIN, etc.
    private int failedAttempts;
    private LocalDateTime lockExpiresAt;
    private boolean isPermanent;
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "UserLocked";
    }
}