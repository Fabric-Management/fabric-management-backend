package com.fabricmanagement.shared.domain.policy;

/**
 * Permission Type Enum
 * 
 * Defines whether a permission is ALLOW or DENY.
 * DENY always wins over ALLOW.
 */
public enum PermissionType {
    
    ALLOW,  // Grant access
    DENY;   // Explicitly deny access (takes precedence)
    
    public boolean isDeny() {
        return this == DENY;
    }
    
    public boolean isAllow() {
        return this == ALLOW;
    }
}
