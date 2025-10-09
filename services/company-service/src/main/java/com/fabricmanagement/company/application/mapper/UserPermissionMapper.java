package com.fabricmanagement.company.application.mapper;

import com.fabricmanagement.company.application.dto.CreateUserPermissionRequest;
import com.fabricmanagement.company.application.dto.UserPermissionResponse;
import com.fabricmanagement.shared.domain.policy.UserPermission;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User Permission Mapper
 * 
 * Maps between UserPermission entity and DTOs.
 */
@Component
public class UserPermissionMapper {
    
    public UserPermission toEntity(CreateUserPermissionRequest request, UUID createdBy) {
        LocalDateTime now = LocalDateTime.now();
        
        return UserPermission.builder()
            .id(UUID.randomUUID())
            .userId(request.getUserId())
            .endpoint(request.getEndpoint())
            .operation(request.getOperation())
            .permissionType(request.getPermissionType())
            .scope(request.getScope())
            .validFrom(now)
            .validUntil(request.getExpiresAt())
            .grantedBy(createdBy)
            .reason(request.getReason())
            .status("ACTIVE")
            .createdAt(now)
            .createdBy(createdBy.toString())
            .updatedAt(now)
            .updatedBy(createdBy.toString())
            .build();
    }
    
    public UserPermissionResponse toResponse(UserPermission entity) {
        String status = entity.getStatus();
        if (entity.getValidUntil() != null && entity.getValidUntil().isBefore(LocalDateTime.now())) {
            status = "EXPIRED";
        }
        
        return UserPermissionResponse.builder()
            .id(entity.getId())
            .userId(entity.getUserId())
            .endpoint(entity.getEndpoint())
            .operation(entity.getOperation())
            .permissionType(entity.getPermissionType())
            .scope(entity.getScope())
            .expiresAt(entity.getValidUntil())
            .reason(entity.getReason())
            .status(status)
            .createdAt(entity.getCreatedAt())
            .createdBy(entity.getCreatedBy() != null ? UUID.fromString(entity.getCreatedBy()) : null)
            .updatedAt(entity.getUpdatedAt())
            .updatedBy(entity.getUpdatedBy() != null ? UUID.fromString(entity.getUpdatedBy()) : null)
            .build();
    }
    
    public List<UserPermissionResponse> toResponseList(List<UserPermission> entities) {
        return entities.stream()
            .map(this::toResponse)
            .toList();
    }
}

