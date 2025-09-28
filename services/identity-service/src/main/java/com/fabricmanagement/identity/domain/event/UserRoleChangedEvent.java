package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user role is changed.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserRoleChangedEvent extends DomainEvent {
    private UUID userId;
    private String oldRole;
    private String newRole;

    public UserRoleChangedEvent(UUID userId, String oldRole, String newRole) {
        super("UserRoleChanged");
        this.userId = userId;
        this.oldRole = oldRole;
        this.newRole = newRole;
    }
}