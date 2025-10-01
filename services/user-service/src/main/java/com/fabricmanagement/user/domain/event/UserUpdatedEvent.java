package com.fabricmanagement.user.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Updated Domain Event
 * 
 * Published when a user's profile information is updated
 */
@Getter
@ToString
@Builder
public class UserUpdatedEvent extends DomainEvent {
    
    private final UUID userId;
    private final String tenantId;
    private final String firstName;
    private final String lastName;
    private final String status;
    private final LocalDateTime timestamp;

    @Override
    public String getEventType() {
        return "UserUpdated";
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
