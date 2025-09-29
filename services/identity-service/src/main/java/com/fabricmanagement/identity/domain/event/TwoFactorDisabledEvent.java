package com.fabricmanagement.identity.domain.event;

/**
 * Event published when two-factor authentication is disabled.
 */
public class TwoFactorDisabledEvent extends DomainEvent {
    
    public TwoFactorDisabledEvent(String userId) {
        super("TwoFactorDisabledEvent", userId);
    }
}