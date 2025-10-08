package com.fabricmanagement.shared.application.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Security Context
 * 
 * Holds current request's security information.
 * Injected into controller methods via @CurrentSecurityContext annotation.
 * 
 * Benefits:
 * - No need to call SecurityContextHolder in every controller method
 * - Cleaner and more testable controller code
 * - Single source of truth for security context data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityContext {
    
    /**
     * Current tenant ID from JWT token
     */
    private UUID tenantId;
    
    /**
     * Current user ID from JWT token
     */
    private String userId;
    
    /**
     * User roles from JWT token
     */
    private String[] roles;
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String role) {
        if (roles == null) {
            return false;
        }
        
        for (String userRole : roles) {
            if (userRole.equals(role) || userRole.equals("ROLE_" + role)) {
                return true;
            }
        }
        
        return false;
    }
}

