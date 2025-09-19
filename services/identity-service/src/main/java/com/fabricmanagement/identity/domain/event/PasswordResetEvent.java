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
public class PasswordResetEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String resetToken;
    private String resetMethod; // EMAIL, SMS, etc.
    private LocalDateTime expiresAt;
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "PasswordReset";
    }
}