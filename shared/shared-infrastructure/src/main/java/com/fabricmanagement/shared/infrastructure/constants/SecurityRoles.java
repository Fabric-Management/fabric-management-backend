package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Security Roles Constants
 * 
 * Centralized role definitions for reference.
 * 
 * ⚠️ CRITICAL: NO USERNAME IN THIS PROJECT!
 * Authentication uses contactValue (email/phone), NOT username.
 * JWT 'sub' claim = userId (UUID), NOT username.
 * See: docs/development/PRINCIPLES.md → "NO USERNAME PRINCIPLE"
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
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String USER = "USER";
    public static final String COMPANY_MANAGER = "COMPANY_MANAGER";
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";
}

