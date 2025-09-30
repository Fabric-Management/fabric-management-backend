package com.fabricmanagement.user.domain.valueobject;

/**
 * Registration Type Enumeration
 * 
 * Represents how a user was registered in the system
 */
public enum RegistrationType {
    /**
     * User registered directly (no invitation needed)
     */
    DIRECT_REGISTRATION,
    
    /**
     * User self-registered as external partner
     */
    SELF_REGISTRATION
}
