package com.fabricmanagement.user.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * User Updated Domain Event
 * 
 * Published when a user's profile information is updated
 */
@Getter
@ToString
public class UserUpdatedEvent extends DomainEvent {
    
    private final UUID userId;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String displayName;

    public UserUpdatedEvent(UUID userId, String username, String firstName, 
                          String lastName, String displayName) {
        super();
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
    }

    @Override
    public String getEventType() {
        return "UserUpdated";
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
