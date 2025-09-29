package com.fabricmanagement.user.infrastructure.integration.identity;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback implementation for Identity Service client.
 * Provides default responses when Identity Service is unavailable.
 */
@Component
public class IdentityServiceFallback implements IdentityServiceClient {
    
    @Override
    public boolean validateToken(String userId, String token) {
        // Return false for security when service is unavailable
        return false;
    }
    
    @Override
    public boolean isUserAuthenticated(String userId) {
        // Return false when service is unavailable
        return false;
    }
    
    @Override
    public List<String> getUserRoles(String userId) {
        // Return empty list when service is unavailable
        return List.of();
    }
    
    @Override
    public boolean hasRole(String userId, String role) {
        // Return false when service is unavailable
        return false;
    }
    
    @Override
    public List<String> getUserPermissions(String userId) {
        // Return empty list when service is unavailable
        return List.of();
    }
    
    @Override
    public boolean hasPermission(String userId, String permission) {
        // Return false when service is unavailable
        return false;
    }
    
    @Override
    public void notifyUserProfileChange(String userId, String changeType) {
        // Log notification failure but don't throw exception
        // This ensures the main flow continues even if Identity Service is down
    }
}
