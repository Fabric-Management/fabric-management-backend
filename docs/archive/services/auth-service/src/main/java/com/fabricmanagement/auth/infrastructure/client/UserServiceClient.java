package com.fabricmanagement.auth.infrastructure.client;

import com.fabricmanagement.shared.infrastructure.config.BaseFeignClientConfig;
import com.fabricmanagement.shared.application.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User Service Client
 * 
 * Feign client for communication with User-Service
 * 
 * ✅ ZERO HARDCODED VALUES - BaseFeignClientConfig kullanıyor
 * ✅ PRODUCTION-READY - Circuit breaker, retry, timeout
 * ✅ INTERNAL ENDPOINT - Internal API Key authentication
 * ✅ JWT PROPAGATION - User context maintained
 */
@FeignClient(
    name = "user-service",
    url = "${services.user-service.url:http://localhost:8082}",
    configuration = BaseFeignClientConfig.class
)
public interface UserServiceClient {
    
    /**
     * Get user profile by ID
     */
    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable UUID userId);
    
    /**
     * Update user status (active/inactive)
     */
    @PutMapping("/api/v1/users/{userId}/status")
    ApiResponse<Void> updateUserStatus(
        @PathVariable UUID userId,
        @RequestBody UpdateUserStatusRequest request
    );
    
    /**
     * Get user roles
     */
    @GetMapping("/api/v1/users/{userId}/roles")
    ApiResponse<List<UserRoleResponse>> getUserRoles(@PathVariable UUID userId);
    
    /**
     * Assign role to user
     */
    @PostMapping("/api/v1/users/{userId}/roles")
    ApiResponse<UserRoleResponse> assignRole(
        @PathVariable UUID userId,
        @RequestBody AssignRoleRequest request
    );
    
    /**
     * Remove role from user
     */
    @DeleteMapping("/api/v1/users/{userId}/roles/{roleName}")
    ApiResponse<Void> removeRole(
        @PathVariable UUID userId,
        @PathVariable String roleName
    );
    
    // =========================================================================
    // REQUEST/RESPONSE DTOs
    // =========================================================================
    
    class UserResponse {
        public UUID id;
        public String firstName;
        public String lastName;
        public String email;
        public Boolean isActive;
        public UUID tenantId;
    }
    
    class UserRoleResponse {
        public UUID id;
        public String roleName;
        public UUID userId;
        public UUID tenantId;
    }
    
    class UpdateUserStatusRequest {
        public Boolean isActive;
        public String reason;
    }
    
    class AssignRoleRequest {
        public String roleName;
        public UUID tenantId;
        public UUID grantedBy;
    }
}
