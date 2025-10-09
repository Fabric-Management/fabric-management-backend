package com.fabricmanagement.shared.domain.policy;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Policy Registry Entity
 * 
 * Centralized endpoint security catalog.
 * Defines platform-wide authorization policies.
 * 
 * Purpose:
 * - Define which company types can access endpoints
 * - Define default roles for endpoints
 * - Store platform policies (JSONB)
 * - Endpoint security documentation
 * 
 * Example:
 * - Endpoint: /api/users
 * - Operation: WRITE
 * - Scope: COMPANY
 * - Allowed Company Types: [INTERNAL]
 * - Default Roles: [ADMIN, HR_MANAGER]
 * 
 * Business Rules:
 * - Endpoint must be unique
 * - Only ACTIVE policies are enforced
 * - Version tracking for audit trail
 */
@Entity
@Table(name = "policy_registry")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PolicyRegistry extends BaseEntity {
    
    @Column(name = "endpoint", nullable = false, unique = true, length = 200)
    private String endpoint;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 50)
    private OperationType operation;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 50)
    private DataScope scope;
    
    /**
     * Company types allowed to access this endpoint
     * Example: ["INTERNAL", "CUSTOMER"]
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_company_types", columnDefinition = "text[]")
    private List<String> allowedCompanyTypes;
    
    /**
     * Roles that have default access to this endpoint
     * Example: ["ADMIN", "USER"]
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "default_roles", columnDefinition = "text[]")
    private List<String> defaultRoles;
    
    @Column(name = "requires_grant")
    @lombok.Builder.Default
    private Boolean requiresGrant = false;
    
    /**
     * Platform policy as JSON
     * Additional rules, guardrails, etc.
     */
    @Column(name = "platform_policy", columnDefinition = "JSONB")
    private String platformPolicy;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
    
    @Column(name = "policy_version", nullable = false, length = 20)
    @lombok.Builder.Default
    private String policyVersion = "v1";
    
    /**
     * Creates a new policy registry entry
     */
    public static PolicyRegistry create(String endpoint, OperationType operation,
                                       DataScope scope, List<String> allowedCompanyTypes) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint cannot be empty");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Scope cannot be null");
        }
        
        return PolicyRegistry.builder()
            .endpoint(endpoint)
            .operation(operation)
            .scope(scope)
            .allowedCompanyTypes(allowedCompanyTypes)
            .active(true)
            .policyVersion("v1")
            .build();
    }
    
    /**
     * Activates the policy
     */
    public void activate() {
        this.active = true;
    }
    
    /**
     * Deactivates the policy
     */
    public void deactivate() {
        this.active = false;
    }
    
    /**
     * Checks if a company type is allowed
     */
    public boolean isCompanyTypeAllowed(String companyType) {
        return allowedCompanyTypes != null && allowedCompanyTypes.contains(companyType);
    }
    
    /**
     * Checks if a role has default access
     */
    public boolean hasRoleAccess(String role) {
        return defaultRoles != null && defaultRoles.contains(role);
    }
}

