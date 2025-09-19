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
public class TwoFactorDisabledEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String previousTwoFactorMethod; // TOTP, SMS, EMAIL, etc.
    private String disabledBy; // USER, ADMIN, SECURITY_INCIDENT, etc.
    private String reason; // USER_REQUEST, SECURITY_BREACH, DEVICE_LOST, etc.
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "TwoFactorDisabled";
    }
}