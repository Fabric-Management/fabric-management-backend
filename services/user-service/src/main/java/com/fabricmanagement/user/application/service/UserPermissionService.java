package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.domain.policy.UserPermission;
import com.fabricmanagement.shared.infrastructure.policy.repository.UserPermissionRepository;
import com.fabricmanagement.user.api.dto.CreateUserPermissionRequest;
import com.fabricmanagement.user.api.dto.UserPermissionResponse;
import com.fabricmanagement.user.application.mapper.UserPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User Permission Service
 * 
 * Business logic for Advanced Settings - User Permission Management.
 * 
 * Responsibilities:
 * - Create/Read/Delete user-specific permissions
 * - Validate permission logic (prevent conflicts)
 * - Handle expiration logic
 * 
 * Note: UserPermission entity lives in shared-domain (used by PolicyEngine)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPermissionService {
    
    private final UserPermissionRepository userPermissionRepository;
    private final UserPermissionMapper userPermissionMapper;
    
    /**
     * Create a new user permission
     */
    @Transactional
    public UUID createPermission(CreateUserPermissionRequest request, UUID createdBy) {
        log.info("Creating permission: user={}, endpoint={}, type={}", 
            request.getUserId(), request.getEndpoint(), request.getPermissionType());
        
        // Validate expiration (if provided, must be in future)
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Expiration date must be in the future");
        }
        
        // Map to entity
        UserPermission permission = userPermissionMapper.toEntity(request, createdBy);
        
        // Save
        UserPermission saved = userPermissionRepository.save(permission);
        
        log.info("Permission created: id={}, user={}", saved.getId(), saved.getUserId());
        return saved.getId();
    }
    
    /**
     * Get all permissions for a user
     */
    @Transactional(readOnly = true)
    public List<UserPermissionResponse> getUserPermissions(UUID userId) {
        log.debug("Getting all permissions for user: {}", userId);
        
        List<UserPermission> permissions = userPermissionRepository
            .findByUserId(userId);
        
        return userPermissionMapper.toResponseList(permissions);
    }
    
    /**
     * Get active (non-expired) permissions for a user
     */
    @Transactional(readOnly = true)
    public List<UserPermissionResponse> getActivePermissions(UUID userId) {
        log.debug("Getting active permissions for user: {}", userId);
        
        List<UserPermission> permissions = userPermissionRepository
            .findEffectivePermissionsForUser(userId, LocalDateTime.now());
        
        return userPermissionMapper.toResponseList(permissions);
    }
    
    /**
     * Get permission by ID
     */
    @Transactional(readOnly = true)
    public UserPermissionResponse getPermission(UUID permissionId) {
        log.debug("Getting permission: {}", permissionId);
        
        UserPermission permission = userPermissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "UserPermission not found with id: " + permissionId));
        
        return userPermissionMapper.toResponse(permission);
    }
    
    /**
     * Delete a permission
     */
    @Transactional
    public void deletePermission(UUID permissionId, UUID deletedBy) {
        log.info("Deleting permission: {} by user: {}", permissionId, deletedBy);
        
        UserPermission permission = userPermissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "UserPermission not found with id: " + permissionId));
        
        userPermissionRepository.delete(permission);
        
        log.info("Permission deleted: id={}, user={}", permissionId, permission.getUserId());
    }
    
    /**
     * Get all permissions (admin view)
     */
    @Transactional(readOnly = true)
    public List<UserPermissionResponse> getAllPermissions() {
        log.debug("Getting all permissions");
        
        List<UserPermission> permissions = userPermissionRepository.findAll();
        return userPermissionMapper.toResponseList(permissions);
    }
}

