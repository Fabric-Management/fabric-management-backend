package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when two-factor authentication is enabled.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TwoFactorEnabledEvent extends DomainEvent {
    private UUID userId;

    public TwoFactorEnabledEvent(UUID userId) {
        super("TwoFactorEnabled");
        this.userId = userId;
    }
}