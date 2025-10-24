package com.fabricmanagement.company.api;

import com.fabricmanagement.company.api.dto.request.CreateUserPermissionRequest;
import com.fabricmanagement.company.api.dto.response.UserPermissionResponse;
import com.fabricmanagement.company.application.service.UserPermissionService;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User Permission Controller (Advanced Settings)
 * 
 * Provides CRUD operations for user-specific permissions.
 * Used by administrators to grant/deny explicit permissions.
 * Uses Spring Security's @AuthenticationPrincipal - 100% framework-native!
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
    
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createPermission(
            @Valid @RequestBody CreateUserPermissionRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Creating user permission: user={}, endpoint={}, type={}", 
            request.getUserId(), request.getEndpoint(), request.getPermissionType());
        
        UUID permissionId = userPermissionService.createPermission(
            request, UUID.fromString(ctx.getUserId()));
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(permissionId, ServiceConstants.MSG_PERMISSION_CREATED));
    }
    
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserPermissionResponse>>> getUserPermissions(
            @PathVariable UUID userId) {
        
        log.debug("Getting permissions for user: {}", userId);
        List<UserPermissionResponse> permissions = userPermissionService.getUserPermissions(userId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }
    
    @GetMapping("/user/{userId}/active")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserPermissionResponse>>> getActivePermissions(
            @PathVariable UUID userId) {
        
        log.debug("Getting active permissions for user: {}", userId);
        List<UserPermissionResponse> permissions = userPermissionService.getActivePermissions(userId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }
    
    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePermission(
            @PathVariable UUID permissionId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Deleting user permission: {}", permissionId);
        userPermissionService.deletePermission(
            permissionId, UUID.fromString(ctx.getUserId()));
        
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_PERMISSION_DELETED));
    }
    
    @GetMapping("/{permissionId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserPermissionResponse>> getPermission(
            @PathVariable UUID permissionId) {
        
        log.debug("Getting permission: {}", permissionId);
        UserPermissionResponse permission = userPermissionService.getPermission(permissionId);
        return ResponseEntity.ok(ApiResponse.success(permission));
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserPermissionResponse>>> getAllPermissions(
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting all permissions for tenant: {}", ctx.getTenantId());
        List<UserPermissionResponse> permissions = userPermissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }
}
