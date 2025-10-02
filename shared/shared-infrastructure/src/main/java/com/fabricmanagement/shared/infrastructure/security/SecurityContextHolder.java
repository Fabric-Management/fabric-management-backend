package com.fabricmanagement.shared.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

import java.util.UUID;

/**
 * Security Context Holder Utility
 * 
 * Provides centralized and safe access to security context
 * Follows PRINCIPLES.md - no random UUID generation
 */
@Slf4j
public final class SecurityContextHolder {
    
    private SecurityContextHolder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * Gets current tenant ID from JWT token
     * 
     * @return tenant ID from security context
     * @throws UnauthorizedException if tenant ID not found or invalid
     */
    public static UUID getCurrentTenantId() {
        Authentication authentication = getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("No authenticated user found in security context");
            throw new UnauthorizedException("Authentication required");
        }
        
        // Extract tenant ID from JWT claims
        Object tenantIdClaim = authentication.getDetails();
        if (tenantIdClaim instanceof String) {
            try {
                return UUID.fromString((String) tenantIdClaim);
            } catch (IllegalArgumentException e) {
                log.error("Invalid tenant ID format in JWT: {}", tenantIdClaim);
                throw new UnauthorizedException("Invalid tenant ID in token");
            }
        }
        
        log.error("Tenant ID not found in authentication details");
        throw new UnauthorizedException("Tenant ID not found in token");
    }
    
    /**
     * Gets current user ID from security context
     * 
     * @return user ID (username/email)
     */
    public static String getCurrentUserId() {
        Authentication authentication = getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("No authenticated user found in security context");
            throw new UnauthorizedException("Authentication required");
        }
        
        String userId = authentication.getName();
        if (userId == null || userId.trim().isEmpty()) {
            log.error("User ID is empty in security context");
            throw new UnauthorizedException("Invalid user in token");
        }
        
        return userId;
    }
    
    /**
     * Checks if current user has a specific role
     * 
     * @param role role to check
     * @return true if user has the role
     */
    public static boolean hasRole(String role) {
        Authentication authentication = getAuthentication();
        
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> 
                    grantedAuthority.getAuthority().equals("ROLE_" + role));
    }
    
    /**
     * Gets authentication from Spring Security context
     */
    private static Authentication getAuthentication() {
        SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        return context.getAuthentication();
    }
}

/**
 * Exception thrown when user is not authorized
 */
class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}

