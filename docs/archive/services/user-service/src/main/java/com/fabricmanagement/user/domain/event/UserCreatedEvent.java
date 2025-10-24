package com.fabricmanagement.user.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Created Domain Event
 * 
 * Published when a new user is created in the system
 */
@Getter
@ToString
@Builder
public class UserCreatedEvent extends DomainEvent {
    
    private final UUID userId;
    private final String tenantId;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String status;
    private final String registrationType;
    private final LocalDateTime timestamp;

    @Override
    public String getEventType() {
        return "UserCreated";
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
