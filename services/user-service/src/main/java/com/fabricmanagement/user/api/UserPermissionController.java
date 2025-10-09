package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.annotation.CurrentSecurityContext;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.api.dto.CreateUserPermissionRequest;
import com.fabricmanagement.user.api.dto.UserPermissionResponse;
import com.fabricmanagement.user.application.service.UserPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User Permission Controller (Advanced Settings)
 * 
 * Provides CRUD operations for user-specific permissions.
 * Used by administrators to grant/deny explicit permissions.
 * 
 * Security: Super Admin only
 * API Version: v1
 * Base Path: /api/v1/user-permissions
 */
@RestController
@RequestMapping("/api/v1/user-permissions")
@RequiredArgsConstructor
@Slf4j
public class UserPermissionController {
    
    private final UserPermissionService userPermissionService;
    
    /**
     * Create a new user permission (grant or deny)
     * 
     * POST /api/v1/user-permissions
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createPermission(
            @Valid @RequestBody CreateUserPermissionRequest request,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.info("Creating user permission: user={}, endpoint={}, type={}", 
            request.getUserId(), request.getEndpoint(), request.getPermissionType());
        
        UUID permissionId = userPermissionService.createPermission(
            request, UUID.fromString(ctx.getUserId()));
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(permissionId, "Permission created successfully"));
    }
    
    /**
     * Get all permissions for a specific user
     * 
     * GET /api/v1/user-permissions/user/{userId}
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserPermissionResponse>>> getUserPermissions(
            @PathVariable UUID userId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Getting permissions for user: {}", userId);
        
        List<UserPermissionResponse> permissions = userPermissionService.getUserPermissions(userId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }
    
    /**
     * Get active permissions for a user (non-expired)
     * 
     * GET /api/v1/user-permissions/user/{userId}/active
     */
    @GetMapping("/user/{userId}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserPermissionResponse>>> getActivePermissions(
            @PathVariable UUID userId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Getting active permissions for user: {}", userId);
        
        List<UserPermissionResponse> permissions = userPermissionService.getActivePermissions(userId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }
    
    /**
     * Delete a user permission
     * 
     * DELETE /api/v1/user-permissions/{permissionId}
     */
    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePermission(
            @PathVariable UUID permissionId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.info("Deleting user permission: {}", permissionId);
        
        userPermissionService.deletePermission(
            permissionId, UUID.fromString(ctx.getUserId()));
        
        return ResponseEntity.ok(ApiResponse.success(null, "Permission deleted successfully"));
    }
    
    /**
     * Get permission by ID
     * 
     * GET /api/v1/user-permissions/{permissionId}
     */
    @GetMapping("/{permissionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserPermissionResponse>> getPermission(
            @PathVariable UUID permissionId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Getting permission: {}", permissionId);
        
        UserPermissionResponse permission = userPermissionService.getPermission(permissionId);
        return ResponseEntity.ok(ApiResponse.success(permission));
    }
    
    /**
     * Get all permissions for the current tenant
     * 
     * GET /api/v1/user-permissions
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserPermissionResponse>>> getAllPermissions(
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Getting all permissions for tenant: {}", ctx.getTenantId());
        
        List<UserPermissionResponse> permissions = userPermissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }
}

