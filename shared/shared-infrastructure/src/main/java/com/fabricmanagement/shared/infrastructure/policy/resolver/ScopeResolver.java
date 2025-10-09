package com.fabricmanagement.shared.infrastructure.policy.resolver;

import com.fabricmanagement.shared.domain.policy.DataScope;
import com.fabricmanagement.shared.domain.policy.PolicyContext;
import com.fabricmanagement.shared.infrastructure.constants.SecurityRoles;
import com.fabricmanagement.shared.infrastructure.policy.constants.PolicyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Scope Resolver
 * 
 * Validates data scope access rights.
 * Ensures users can only access data within their authorized scope.
 * 
 * Scope Hierarchy:
 * - SELF: Own data only (most restrictive)
 * - COMPANY: Company-wide data
 * - CROSS_COMPANY: Multi-company data (requires trust relationship)
 * - GLOBAL: System-wide data (Super Admin only, least restrictive)
 * 
 * Design Principles:
 * - Stateless (no instance variables)
 * - Fail-safe (deny by default)
 * - Explicit ownership validation
 * - Clear denial reasons
 * 
 * Usage:
 * <pre>
 * String denialReason = scopeResolver.validateScope(context);
 * if (denialReason != null) {
 *     return PolicyDecision.deny(denialReason, ...);
 * }
 * </pre>
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Slf4j
@Component
public class ScopeResolver {
    
    private static final String SCOPE_PREFIX = PolicyConstants.REASON_SCOPE;
    
    /**
     * Validate if user can access resource within the scope
     * 
     * @param context policy context
     * @return denial reason if scope invalid, null if valid
     */
    public String validateScope(PolicyContext context) {
        DataScope scope = context.getEffectiveScope();
        
        // Validate based on scope type
        return switch (scope) {
            case SELF -> validateSelfScope(context);
            case COMPANY -> validateCompanyScope(context);
            case CROSS_COMPANY -> validateCrossCompanyScope(context);
            case GLOBAL -> validateGlobalScope(context);
        };
    }
    
    /**
     * Validate SELF scope
     * User can only access their own data
     * 
     * @param context policy context
     * @return denial reason if invalid
     */
    private String validateSelfScope(PolicyContext context) {
        UUID resourceOwnerId = context.getResourceOwnerId();
        UUID userId = context.getUserId();
        
        // For list operations, no specific resource owner
        if (resourceOwnerId == null) {
            // List operations with SELF scope should be filtered by service layer
            return null;
        }
        
        // Check if accessing own data
        if (!resourceOwnerId.equals(userId)) {
            log.info("User {} attempted to access resource owned by {} with SELF scope. Denied.",
                userId, resourceOwnerId);
            
            return SCOPE_PREFIX + "_self_not_owner";
        }
        
        return null; // Valid
    }
    
    /**
     * Validate COMPANY scope
     * User can access data within their company
     * 
     * @param context policy context
     * @return denial reason if invalid
     */
    private String validateCompanyScope(PolicyContext context) {
        UUID userCompanyId = context.getCompanyId();
        UUID resourceCompanyId = context.getResourceCompanyId();
        
        // User must belong to a company
        if (userCompanyId == null) {
            log.warn("User {} has no company but requires COMPANY scope. Denied.",
                context.getUserId());
            
            return SCOPE_PREFIX + "_company_user_no_company";
        }
        
        // For list operations, no specific resource company
        if (resourceCompanyId == null) {
            // List operations should be filtered by service layer
            return null;
        }
        
        // Check if accessing same company data
        if (!resourceCompanyId.equals(userCompanyId)) {
            log.info("User from company {} attempted to access resource from company {} with COMPANY scope. Denied.",
                userCompanyId, resourceCompanyId);
            
            return SCOPE_PREFIX + "_company_different_company";
        }
        
        return null; // Valid
    }
    
    /**
     * Validate CROSS_COMPANY scope
     * User can access data from multiple companies (requires trust relationship)
     * 
     * @param context policy context
     * @return denial reason if invalid
     */
    private String validateCrossCompanyScope(PolicyContext context) {
        UUID userCompanyId = context.getCompanyId();
        UUID resourceCompanyId = context.getResourceCompanyId();
        
        // User must belong to a company
        if (userCompanyId == null) {
            log.warn("User {} has no company but requires CROSS_COMPANY scope. Denied.",
                context.getUserId());
            
            return SCOPE_PREFIX + "_cross_company_user_no_company";
        }
        
        // If accessing same company, always allowed
        if (resourceCompanyId != null && resourceCompanyId.equals(userCompanyId)) {
            return null; // Same company access always allowed
        }
        
        // For different company access, check trust relationship
        // Future improvement: Call Company Service API to check CompanyRelationship
        // Current logic: Allow CROSS_COMPANY scope for INTERNAL users only (safe default)
        if (context.isInternal()) {
            return null; // Internal users can access cross-company data
        }
        
        log.info("External user from company {} attempted CROSS_COMPANY access to company {}. Denied.",
            userCompanyId, resourceCompanyId);
        
        return SCOPE_PREFIX + "_cross_company_no_relationship";
    }
    
    /**
     * Validate GLOBAL scope
     * User can access system-wide data (Super Admin only)
     * 
     * @param context policy context
     * @return denial reason if invalid
     */
    private String validateGlobalScope(PolicyContext context) {
        // Only Super Admin can have GLOBAL scope
        if (!context.hasAnyRole(SecurityRoles.SUPER_ADMIN, SecurityRoles.SYSTEM_ADMIN)) {
            log.warn("User {} attempted GLOBAL scope access without SUPER_ADMIN role. Denied.",
                context.getUserId());
            
            return SCOPE_PREFIX + "_global_not_admin";
        }
        
        return null; // Valid
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Check if user can access resource (simple check)
     * 
     * @param userId user ID
     * @param resourceOwnerId resource owner ID
     * @param userCompanyId user's company ID
     * @param resourceCompanyId resource's company ID
     * @param scope data scope
     * @return true if access allowed
     */
    public boolean canAccess(UUID userId, UUID resourceOwnerId, 
                            UUID userCompanyId, UUID resourceCompanyId,
                            DataScope scope) {
        if (scope == null) {
            scope = DataScope.SELF; // Default to most restrictive
        }
        
        return switch (scope) {
            case SELF -> resourceOwnerId != null && resourceOwnerId.equals(userId);
            case COMPANY -> resourceCompanyId != null && resourceCompanyId.equals(userCompanyId);
            case CROSS_COMPANY -> true; // Requires additional relationship check
            case GLOBAL -> true; // Role check should be done separately
        };
    }
    
    /**
     * Determine appropriate scope for operation
     * Based on endpoint pattern
     * 
     * @param endpoint API endpoint
     * @return suggested data scope
     */
    public DataScope inferScopeFromEndpoint(String endpoint) {
        if (endpoint == null) {
            return DataScope.SELF; // Default to most restrictive
        }
        
        // /me or /profile endpoints → SELF
        if (endpoint.contains("/me") || endpoint.contains("/profile")) {
            return DataScope.SELF;
        }
        
        // /company or /department endpoints → COMPANY
        if (endpoint.contains("/company") || endpoint.contains("/department")) {
            return DataScope.COMPANY;
        }
        
        // /admin or /system endpoints → GLOBAL
        if (endpoint.contains("/admin") || endpoint.contains("/system")) {
            return DataScope.GLOBAL;
        }
        
        // Default to COMPANY scope
        return DataScope.COMPANY;
    }
}

