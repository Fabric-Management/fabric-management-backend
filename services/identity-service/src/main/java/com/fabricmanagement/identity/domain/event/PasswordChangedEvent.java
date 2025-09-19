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
public class PasswordChangedEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String changeReason; // USER_REQUEST, EXPIRED, SECURITY_POLICY, etc.
    private boolean wasTemporary;
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "PasswordChanged";
    }
}