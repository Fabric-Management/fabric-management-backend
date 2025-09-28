package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a password is created.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PasswordCreatedEvent extends DomainEvent {
    private UUID userId;

    public PasswordCreatedEvent(UUID userId) {
        super("PasswordCreated");
        this.userId = userId;
    }
}