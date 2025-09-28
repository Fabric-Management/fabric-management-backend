package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when two-factor authentication is disabled.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TwoFactorDisabledEvent extends DomainEvent {
    private UUID userId;

    public TwoFactorDisabledEvent(UUID userId) {
        super("TwoFactorDisabled");
        this.userId = userId;
    }
}