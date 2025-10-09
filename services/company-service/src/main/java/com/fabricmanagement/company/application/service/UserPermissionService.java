package com.fabricmanagement.company.application.service;

import com.fabricmanagement.company.application.dto.CreateUserPermissionRequest;
import com.fabricmanagement.company.application.dto.UserPermissionResponse;
import com.fabricmanagement.company.application.mapper.UserPermissionMapper;
import com.fabricmanagement.shared.domain.policy.UserPermission;
import com.fabricmanagement.shared.infrastructure.policy.repository.UserPermissionRepository;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPermissionService {
    
    private final UserPermissionRepository userPermissionRepository;
    private final UserPermissionMapper userPermissionMapper;
    
    @Transactional
    public UUID createPermission(CreateUserPermissionRequest request, UUID createdBy) {
        log.info("Creating permission: user={}, endpoint={}, type={}", 
            request.getUserId(), request.getEndpoint(), request.getPermissionType());
        
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Expiration date must be in the future");
        }
        
        UserPermission permission = userPermissionMapper.toEntity(request, createdBy);
        UserPermission saved = userPermissionRepository.save(permission);
        
        log.info("Permission created: id={}, user={}", saved.getId(), saved.getUserId());
        return saved.getId();
    }
    
    @Transactional(readOnly = true)
    public List<UserPermissionResponse> getUserPermissions(UUID userId) {
        log.debug("Getting all permissions for user: {}", userId);
        List<UserPermission> permissions = userPermissionRepository.findByUserId(userId);
        return userPermissionMapper.toResponseList(permissions);
    }
    
    @Transactional(readOnly = true)
    public List<UserPermissionResponse> getActivePermissions(UUID userId) {
        log.debug("Getting active permissions for user: {}", userId);
        List<UserPermission> permissions = userPermissionRepository
            .findEffectivePermissionsForUser(userId, LocalDateTime.now());
        return userPermissionMapper.toResponseList(permissions);
    }
    
    @Transactional(readOnly = true)
    public UserPermissionResponse getPermission(UUID permissionId) {
        log.debug("Getting permission: {}", permissionId);
        UserPermission permission = userPermissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "UserPermission not found with id: " + permissionId));
        return userPermissionMapper.toResponse(permission);
    }
    
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
    
    @Transactional(readOnly = true)
    public List<UserPermissionResponse> getAllPermissions() {
        log.debug("Getting all permissions");
        List<UserPermission> permissions = userPermissionRepository.findAll();
        return userPermissionMapper.toResponseList(permissions);
    }
}

