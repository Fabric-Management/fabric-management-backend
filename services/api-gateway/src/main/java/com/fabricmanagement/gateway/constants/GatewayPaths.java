package com.fabricmanagement.gateway.constants;

import java.util.List;

/**
 * Gateway Path Constants
 * 
 * Centralized path patterns for API Gateway.
 * Defines which endpoints are public (no auth required).
 * 
 * Single source of truth - NO hardcoded path strings!
 */
public final class GatewayPaths {
    
    /**
     * Public endpoints (no JWT authentication required)
     */
    public static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/users/auth/",           // All authentication endpoints
        "/api/v1/contacts/find-by-value", // Internal contact lookup (for auth)
        "/actuator/health",              // Health check
        "/actuator/info",                // Info endpoint  
        "/actuator/prometheus",          // Prometheus metrics
        "/fallback/",                    // Fallback endpoints
        "/gateway/"                      // Gateway management endpoints
    );
    
    /**
     * Check if path is public endpoint
     * 
     * @param path Request path
     * @return true if public, false if protected
     */
    public static boolean isPublic(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
    
    private GatewayPaths() {
        throw new UnsupportedOperationException("Utility class - cannot instantiate");
    }
}

