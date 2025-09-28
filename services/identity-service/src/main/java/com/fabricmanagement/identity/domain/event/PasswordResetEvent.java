package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a password is reset.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PasswordResetEvent extends DomainEvent {
    private UUID userId;

    public PasswordResetEvent(UUID userId) {
        super("PasswordReset");
        this.userId = userId;
    }
}