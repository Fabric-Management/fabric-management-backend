package com.fabricmanagement.user.domain.valueobject;

/**
 * User Status Enumeration
 * 
 * Represents the possible states of a user account
 */
public enum UserStatus {
    /**
     * User account is active and can be used
     */
    ACTIVE,
    
    /**
     * User account is inactive (temporarily disabled)
     */
    INACTIVE,
    
    /**
     * User account is pending activation
     */
    PENDING,
    
    /**
     * User account is suspended
     */
    SUSPENDED,
    
    /**
     * User account is deleted (soft delete)
     */
    DELETED
}
