package com.fabricmanagement.shared.domain.policy;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Permission Entity
 * 
 * Represents user-specific permission grants/denies.
 * Used for Advanced Settings - endpoint-level permissions.
 * 
 * Example:
 * - User: john@example.com
 * - Endpoint: /api/users/{id}
 * - Operation: WRITE
 * - Scope: COMPANY
 * - Type: ALLOW
 * - Valid Until: 2025-12-31
 * 
 * Business Rules:
 * - DENY permissions take precedence over ALLOW
 * - Permissions can be time-bound (TTL)
 * - Only ACTIVE permissions are enforced
 */
@Entity
@Table(name = "user_permissions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserPermission extends BaseEntity {
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 50)
    private OperationType operation;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 50)
    private DataScope scope;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", nullable = false, length = 20)
    private PermissionType permissionType;
    
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    @Column(name = "valid_until")
    private LocalDateTime validUntil;
    
    @Column(name = "granted_by")
    private UUID grantedBy;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "status", nullable = false, length = 20)
    @lombok.Builder.Default
    private String status = "ACTIVE";  // ACTIVE, EXPIRED, REVOKED
    
    /**
     * Creates a new user permission grant
     */
    public static UserPermission create(UUID userId, String endpoint, OperationType operation,
                                       DataScope scope, PermissionType permissionType,
                                       UUID grantedBy) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint cannot be empty");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Scope cannot be null");
        }
        if (permissionType == null) {
            throw new IllegalArgumentException("Permission type cannot be null");
        }
        
        return UserPermission.builder()
            .userId(userId)
            .endpoint(endpoint)
            .operation(operation)
            .scope(scope)
            .permissionType(permissionType)
            .grantedBy(grantedBy)
            .validFrom(LocalDateTime.now())
            .status("ACTIVE")
            .build();
    }
    
    /**
     * Checks if permission is currently effective
     */
    public boolean isEffective() {
        if (!"ACTIVE".equals(status)) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        
        if (validUntil != null && now.isAfter(validUntil)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if permission is expired
     */
    public boolean isExpired() {
        if (validUntil == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(validUntil);
    }
    
    /**
     * Revokes the permission
     */
    public void revoke() {
        this.status = "REVOKED";
    }
    
    /**
     * Marks permission as expired
     */
    public void markExpired() {
        this.status = "EXPIRED";
    }
}

