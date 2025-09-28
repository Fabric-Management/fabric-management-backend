package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a password is changed.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PasswordChangedEvent extends DomainEvent {
    private UUID userId;

    public PasswordChangedEvent(UUID userId) {
        super("PasswordChanged");
        this.userId = userId;
    }
}