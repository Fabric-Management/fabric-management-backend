package com.fabricmanagement.user.domain.event;

import java.util.UUID;

public class UserDeactivatedEvent extends UserDomainEvent {

    public UserDeactivatedEvent(UUID userId) {
        super(userId);
    }
}