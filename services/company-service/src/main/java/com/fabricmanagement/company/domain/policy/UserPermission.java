package com.fabricmanagement.company.domain.policy;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.shared.domain.policy.DataScope;
import com.fabricmanagement.shared.domain.policy.OperationType;
import com.fabricmanagement.shared.domain.policy.PermissionType;
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
 * User-specific permission grants (Advanced Settings).
 * Managed by Company Service, used by all services via PDP.
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
    private String status = "ACTIVE";
    
    public boolean isActive() {
        return "ACTIVE".equals(status) && !isExpired() && !isDeleted();
    }
    
    public boolean isExpired() {
        return validUntil != null && validUntil.isBefore(LocalDateTime.now());
    }
    
    public void expire() {
        this.status = "EXPIRED";
    }
    
    public void revoke() {
        this.status = "REVOKED";
    }
    
    public boolean isDeny() {
        return PermissionType.DENY.equals(permissionType);
    }
    
    public boolean isAllow() {
        return PermissionType.ALLOW.equals(permissionType);
    }
}

