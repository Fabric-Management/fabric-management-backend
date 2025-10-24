package com.fabricmanagement.user.domain.valueobject;

/**
 * Registration Type Enumeration
 * 
 * Represents how a user was registered in the system
 */
public enum RegistrationType {
    /**
     * User created by the system (e.g., initial admin user)
     */
    SYSTEM_CREATED,
    
    /**
     * User registered directly (no invitation needed)
     */
    DIRECT_REGISTRATION,
    
    /**
     * User self-registered as external partner
     */
    SELF_REGISTRATION
}