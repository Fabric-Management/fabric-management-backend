package com.fabricmanagement.user.domain.valueobject;

/**
 * User Status Enumeration
 * 
 * Represents the possible states of a user account
 */
public enum UserStatus {
    // Registration statuses
    PENDING_VERIFICATION,       // User registered, waiting for contact verification
    ACTIVE,                     // User fully verified and active
    
    // Self-registration statuses (for external partners)
    PENDING_APPROVAL,           // Self-registered, waiting for admin approval
    APPROVED,                   // Admin approved, can access dashboard
    
    // System statuses
    INACTIVE,                   // Temporarily disabled
    SUSPENDED,                  // Suspended by admin
    DELETED                     // Soft deleted
}
