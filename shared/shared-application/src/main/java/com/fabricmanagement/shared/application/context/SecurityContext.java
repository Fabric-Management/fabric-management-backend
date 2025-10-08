package com.fabricmanagement.shared.application.context;

import com.fabricmanagement.shared.domain.policy.CompanyType;
import com.fabricmanagement.shared.domain.policy.DataScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Security Context
 * 
 * Holds current request's security information.
 * Injected into controller methods via @CurrentSecurityContext annotation.
 * 
 * Extended for Policy-Based Authorization:
 * - Company context (companyId, companyType)
 * - Department context (departmentId, jobTitle)
 * - Permissions and scope information
 * 
 * Benefits:
 * - No need to call SecurityContextHolder in every controller method
 * - Cleaner and more testable controller code
 * - Single source of truth for security context data
 * - Policy-aware authorization support
 * 
 * Version: 2.0 (Policy-enabled)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityContext {
    
    // =========================================================================
    // EXISTING FIELDS (V1.0 - Keep as-is for backward compatibility)
    // =========================================================================
    
    /**
     * Current tenant ID from JWT token
     */
    private UUID tenantId;
    
    /**
     * Current user ID from JWT token
     */
    private String userId;
    
    /**
     * User roles from JWT token
     * Example: ["ADMIN", "MANAGER"]
     */
    private String[] roles;
    
    // =========================================================================
    // NEW FIELDS (V2.0 - Policy Authorization)
    // =========================================================================
    
    /**
     * Company ID that user belongs to
     * 
     * Nullable: System users (Super Admin) may not belong to a company
     */
    private UUID companyId;
    
    /**
     * Company type for policy guardrails
     * 
     * Critical for authorization:
     * - INTERNAL: Full access (read/write/delete)
     * - CUSTOMER: Read-only access
     * - SUPPLIER: Limited write (purchase orders)
     * - SUBCONTRACTOR: Limited write (production orders)
     * 
     * Nullable: Will be null for system users
     */
    private CompanyType companyType;
    
    /**
     * Department ID that user belongs to
     * 
     * Only for INTERNAL users. External users (CUSTOMER/SUPPLIER) don't have departments.
     * Used for department-based dashboard routing.
     * 
     * Nullable: External users or users without department assignment
     */
    private UUID departmentId;
    
    /**
     * User's job title
     * 
     * Examples: "Dokumacı", "Muhasebeci", "Kalite Kontrolcü"
     * Used for display purposes and dashboard routing.
     * 
     * Nullable: Optional field
     */
    private String jobTitle;
    
    /**
     * Effective permissions for this user
     * 
     * Combined from:
     * - Role default permissions
     * - User-specific grants (from Advanced Settings)
     * 
     * Format: ["READ:USER/SELF", "WRITE:CONTACT/COMPANY", "DELETE:ORDER/SELF"]
     * 
     * Note: Can bloat JWT if too many permissions. Consider storing only
     * policyVersion hash and fetching from PDP/Redis if size > 8KB.
     * 
     * Nullable: Will be populated by PDP if needed
     */
    private List<String> permissions;
    
    /**
     * Default data scope for this user
     * 
     * Determines default data access boundary:
     * - SELF: Own data only
     * - COMPANY: Company-wide data
     * - CROSS_COMPANY: Multi-company data
     * - GLOBAL: System-wide data
     * 
     * Can be overridden by specific endpoint policies.
     * 
     * Nullable: Falls back to SELF if not specified
     */
    private DataScope defaultScope;
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Check if user has a specific role
     * 
     * @param role role name (without "ROLE_" prefix)
     * @return true if user has the role
     */
    public boolean hasRole(String role) {
        if (roles == null) {
            return false;
        }
        
        for (String userRole : roles) {
            if (userRole.equals(role) || userRole.equals("ROLE_" + role)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if user has any of the specified roles
     * 
     * @param requiredRoles roles to check
     * @return true if user has at least one role
     */
    public boolean hasAnyRole(String... requiredRoles) {
        if (roles == null || requiredRoles == null) {
            return false;
        }
        
        return Arrays.stream(requiredRoles)
            .anyMatch(this::hasRole);
    }
    
    /**
     * Check if user has a specific permission
     * 
     * @param permission permission string (e.g., "WRITE:USER/COMPANY")
     * @return true if user has the permission
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    /**
     * Check if user is from internal company
     * 
     * @return true if company type is INTERNAL
     */
    public boolean isInternal() {
        return companyType != null && companyType.isInternal();
    }
    
    /**
     * Check if user is from external company (customer/supplier)
     * 
     * @return true if company type is CUSTOMER, SUPPLIER, or SUBCONTRACTOR
     */
    public boolean isExternal() {
        return companyType != null && companyType.isExternal();
    }
    
    /**
     * Gets effective data scope
     * Falls back to SELF if not specified
     * 
     * @return data scope (never null)
     */
    public DataScope getEffectiveScope() {
        return defaultScope != null ? defaultScope : DataScope.SELF;
    }
    
    /**
     * Check if user has department assignment
     * 
     * @return true if departmentId is not null
     */
    public boolean hasDepartment() {
        return departmentId != null;
    }
}

