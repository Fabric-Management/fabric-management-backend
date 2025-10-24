package com.fabricmanagement.shared.infrastructure.policy.constants;

/**
 * Policy System Constants
 * 
 * Centralized constants for policy authorization system.
 * Prevents magic strings and numbers throughout the codebase.
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
public final class PolicyConstants {
    
    // Private constructor to prevent instantiation
    private PolicyConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // =========================================================================
    // POLICY DECISIONS
    // =========================================================================
    
    public static final String DECISION_ALLOW = "ALLOW";
    public static final String DECISION_DENY = "DENY";
    
    // =========================================================================
    // POLICY VERSIONS
    // =========================================================================
    
    public static final String POLICY_VERSION_V1 = "v1.0";
    public static final String POLICY_VERSION_DEFAULT = POLICY_VERSION_V1;
    
    // =========================================================================
    // CACHE SETTINGS
    // =========================================================================
    
    public static final int CACHE_TTL_MINUTES = 5;
    public static final String CACHE_KEY_SEPARATOR = "::";
    
    // =========================================================================
    // PERMISSION STATUS
    // =========================================================================
    
    public static final String PERMISSION_STATUS_ACTIVE = "ACTIVE";
    public static final String PERMISSION_STATUS_EXPIRED = "EXPIRED";
    public static final String PERMISSION_STATUS_REVOKED = "REVOKED";
    
    // =========================================================================
    // DENY REASON PREFIXES
    // =========================================================================
    
    public static final String REASON_GUARDRAIL = "company_type_guardrail";
    public static final String REASON_PLATFORM = "platform_policy";
    public static final String REASON_USER_GRANT = "user_grant";
    public static final String REASON_SCOPE = "scope_violation";
    public static final String REASON_ROLE = "role_no_default_access";
    public static final String REASON_ERROR = "policy_evaluation_error";
}

