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
public class UserReactivatedEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String reactivationReason;
    private String reactivatedBy; // ADMIN, SYSTEM, AUTO_EXPIRY, etc.
    private String previousSuspensionReason;
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "UserReactivated";
    }
}