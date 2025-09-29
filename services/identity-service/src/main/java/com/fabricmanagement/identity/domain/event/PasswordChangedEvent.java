package com.fabricmanagement.identity.domain.event;

/**
 * Event published when user password is changed.
 */
public class PasswordChangedEvent extends DomainEvent {
    
    public PasswordChangedEvent(String userId) {
        super("PasswordChangedEvent", userId);
    }
}