package com.fabricmanagement.user.domain.event;

import java.util.UUID;

public class UserCreatedEvent extends UserDomainEvent {

    public UserCreatedEvent(UUID userId) {
        super(userId);
    }
}