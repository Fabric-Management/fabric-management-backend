package com.fabricmanagement.user.infrastructure.integration.identity;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Feign client for Identity Service integration.
 * Provides REST API communication with Identity Service.
 */
@FeignClient(
    name = "identity-service",
    url = "${identity.service.url:http://identity-service:8081}",
    fallback = IdentityServiceFallback.class
)
public interface IdentityServiceClient {
    
    /**
     * Validates authentication token.
     *
     * @param userId the user ID
     * @param token the authentication token
     * @return true if token is valid, false otherwise
     */
    @PostMapping("/api/v1/auth/validate")
    boolean validateToken(@RequestParam("userId") String userId, 
                         @RequestParam("token") String token);
    
    /**
     * Checks if user is authenticated.
     *
     * @param userId the user ID
     * @return true if user is authenticated, false otherwise
     */
    @GetMapping("/api/v1/auth/status/{userId}")
    boolean isUserAuthenticated(@PathVariable("userId") String userId);
    
    /**
     * Gets user roles.
     *
     * @param userId the user ID
     * @return list of user roles
     */
    @GetMapping("/api/v1/auth/roles/{userId}")
    List<String> getUserRoles(@PathVariable("userId") String userId);
    
    /**
     * Checks if user has specific role.
     *
     * @param userId the user ID
     * @param role the role to check
     * @return true if user has the role, false otherwise
     */
    @GetMapping("/api/v1/auth/roles/{userId}/has/{role}")
    boolean hasRole(@PathVariable("userId") String userId, 
                   @PathVariable("role") String role);
    
    /**
     * Gets user permissions.
     *
     * @param userId the user ID
     * @return list of user permissions
     */
    @GetMapping("/api/v1/auth/permissions/{userId}")
    List<String> getUserPermissions(@PathVariable("userId") String userId);
    
    /**
     * Checks if user has specific permission.
     *
     * @param userId the user ID
     * @param permission the permission to check
     * @return true if user has the permission, false otherwise
     */
    @GetMapping("/api/v1/auth/permissions/{userId}/has/{permission}")
    boolean hasPermission(@PathVariable("userId") String userId, 
                         @PathVariable("permission") String permission);
    
    /**
     * Notifies Identity Service about user profile changes.
     *
     * @param userId the user ID
     * @param changeType the type of change
     */
    @PostMapping("/api/v1/auth/notify/profile-change")
    void notifyUserProfileChange(@RequestParam("userId") String userId, 
                               @RequestParam("changeType") String changeType);
}
