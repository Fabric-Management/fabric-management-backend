package com.fabricmanagement.user.api.dto;

import com.fabricmanagement.shared.domain.policy.DataScope;
import com.fabricmanagement.shared.domain.policy.OperationType;
import com.fabricmanagement.shared.domain.policy.PermissionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Permission Response DTO
 * 
 * Returns user permission details from Advanced Settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionResponse {
    
    private UUID id;
    private UUID userId;
    private String endpoint;
    private OperationType operation;
    private PermissionType permissionType;
    private DataScope scope;
    private LocalDateTime expiresAt;
    private String reason;
    private String status; // ACTIVE, EXPIRED
    private LocalDateTime createdAt;
    private UUID createdBy;
    private LocalDateTime updatedAt;
    private UUID updatedBy;
}

