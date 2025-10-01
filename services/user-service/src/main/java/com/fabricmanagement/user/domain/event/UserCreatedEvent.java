package com.fabricmanagement.user.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * User Created Domain Event
 * 
 * Published when a new user is created in the system
 */
@Getter
@ToString
public class UserCreatedEvent extends DomainEvent {
    
    private final UUID userId;
    private final String contactValue;
    private final String firstName;
    private final String lastName;

    public UserCreatedEvent(UUID userId, String contactValue, 
                          String firstName, String lastName) {
        super();
        this.userId = userId;
        this.contactValue = contactValue;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public String getEventType() {
        return "UserCreated";
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
