package com.fabricmanagement.user.domain.event;

import java.util.UUID;

public class UserSuspendedEvent extends UserDomainEvent {

    public UserSuspendedEvent(UUID userId) {
        super(userId);
    }
}