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
public class UserAuthenticatedEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private String authenticationMethod; // PASSWORD, TWO_FACTOR, SSO, etc.
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private boolean isSuccessful;
    private String failureReason; // if not successful
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "UserAuthenticated";
    }
}