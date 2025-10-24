package com.fabricmanagement.user.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Deleted Domain Event
 * 
 * Published when a user is soft deleted from the system
 */
@Getter
@ToString
@Builder
public class UserDeletedEvent extends DomainEvent {
    
    private final UUID userId;
    private final String tenantId;
    private final LocalDateTime timestamp;

    @Override
    public String getEventType() {
        return "UserDeleted";
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
