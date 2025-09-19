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
public class TwoFactorEnabledEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String twoFactorMethod; // TOTP, SMS, EMAIL, etc.
    private String enabledBy; // USER, ADMIN, SECURITY_POLICY, etc.
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "TwoFactorEnabled";
    }
}