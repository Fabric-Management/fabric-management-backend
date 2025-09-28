package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user is created.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserCreatedEvent extends DomainEvent {
    private UUID userId;
    private UUID tenantId;
    private String username;

    public UserCreatedEvent(UUID userId, UUID tenantId, String username) {
        super("UserCreated");
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
    }
}