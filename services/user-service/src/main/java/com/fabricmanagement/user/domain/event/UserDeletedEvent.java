package com.fabricmanagement.user.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * User Deleted Domain Event
 * 
 * Published when a user is soft deleted from the system
 */
@Getter
@ToString
public class UserDeletedEvent extends DomainEvent {
    
    private final UUID userId;
    private final String username;
    private final String email;

    public UserDeletedEvent(UUID userId, String username, String email) {
        super();
        this.userId = userId;
        this.username = username;
        this.email = email;
    }

    @Override
    public String getEventType() {
        return "UserDeleted";
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
