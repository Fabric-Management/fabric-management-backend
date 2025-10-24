package com.fabricmanagement.auth.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Permission Aggregate
 * 
 * Represents granular user permissions for authorization
 * Extends BaseEntity for UUID id, audit fields, soft delete
 */
@Entity
@Table(name = "auth_user_permissions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserPermission extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "granted_at", nullable = false)
    @lombok.Builder.Default
    private LocalDateTime grantedAt = LocalDateTime.now();

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // =========================================================================
    // BUSINESS METHODS
    // =========================================================================

    /**
     * Check if permission is active
     */
    public boolean isActive() {
        return grantedAt != null;
    }

    /**
     * Check if permission applies to specific resource
     */
    public boolean appliesToResource(String resourceType, UUID resourceId) {
        if (this.resourceType == null || this.resourceId == null) {
            return true; // Global permission
        }
        return this.resourceType.equals(resourceType) && this.resourceId.equals(resourceId);
    }
}
