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
 * User Role Aggregate
 * 
 * Represents user role assignments for authorization
 * Extends BaseEntity for UUID id, audit fields, soft delete
 */
@Entity
@Table(name = "auth_user_roles")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserRole extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

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
     * Check if role is active
     */
    public boolean isActive() {
        return grantedAt != null;
    }
}
