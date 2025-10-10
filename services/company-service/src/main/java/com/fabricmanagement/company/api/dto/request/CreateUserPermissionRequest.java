package com.fabricmanagement.company.api.dto.request;

import com.fabricmanagement.shared.domain.policy.DataScope;
import com.fabricmanagement.shared.domain.policy.OperationType;
import com.fabricmanagement.shared.domain.policy.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Create User Permission Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserPermissionRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotBlank(message = "Endpoint is required")
    private String endpoint;
    
    @NotNull(message = "Operation type is required")
    private OperationType operation;
    
    @NotNull(message = "Permission type is required (ALLOW/DENY)")
    private PermissionType permissionType;
    
    @NotNull(message = "Data scope is required")
    private DataScope scope;
    
    private LocalDateTime expiresAt;
    private String reason;
}

