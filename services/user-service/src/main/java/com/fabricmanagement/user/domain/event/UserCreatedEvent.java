package com.fabricmanagement.user.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
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
public class UserCreatedEvent extends DomainEvent {
    
    private final UUID userId;
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;

    public UserCreatedEvent(UUID userId, String username, String email, 
                          String firstName, String lastName) {
        super();
        this.userId = userId;
        this.username = username;
        this.email = email;
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
