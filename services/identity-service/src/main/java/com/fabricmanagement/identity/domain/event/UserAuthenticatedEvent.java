package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user is authenticated.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserAuthenticatedEvent extends DomainEvent {
    private UUID userId;
    private String ipAddress;

    public UserAuthenticatedEvent(UUID userId, String ipAddress) {
        super("UserAuthenticated");
        this.userId = userId;
        this.ipAddress = ipAddress;
    }
}