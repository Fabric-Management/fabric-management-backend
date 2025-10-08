package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Security Roles Constants
 * 
 * Centralized role definitions for reference.
 * 
 * Usage in controllers:
 * @PreAuthorize("hasRole('ADMIN')")           // Single role
 * @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")  // Multiple roles
 * @PreAuthorize("isAuthenticated()")          // Any authenticated user
 * 
 * Note: These are reference constants. Use standard Spring @PreAuthorize annotation directly.
 */
public final class SecurityRoles {
    
    private SecurityRoles() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // Role names - for reference only
    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";
    public static final String COMPANY_MANAGER = "COMPANY_MANAGER";
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";
}

