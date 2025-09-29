package com.fabricmanagement.identity.domain.event;

/**
 * Event published when two-factor authentication is enabled.
 */
public class TwoFactorEnabledEvent extends DomainEvent {
    
    public TwoFactorEnabledEvent(String userId) {
        super("TwoFactorEnabledEvent", userId);
    }
}