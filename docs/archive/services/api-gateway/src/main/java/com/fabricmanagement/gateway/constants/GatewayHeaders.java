package com.fabricmanagement.gateway.constants;

/**
 * Gateway Header Constants
 * 
 * Centralized header name constants for API Gateway.
 * Used by filters to add/read headers in requests.
 * 
 * Single source of truth - NO hardcoded header strings!
 */
public final class GatewayHeaders {
    
    // Security context headers (added by JwtAuthenticationFilter)
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String COMPANY_ID = "X-Company-Id";
    
    // Policy decision headers (added by PolicyEnforcementFilter)
    public static final String POLICY_DECISION = "X-Policy-Decision";
    public static final String POLICY_REASON = "X-Policy-Reason";
    public static final String POLICY_DENIAL_REASON = "X-Policy-Denial-Reason";
    
    // Correlation headers (for tracing)
    public static final String CORRELATION_ID = "X-Correlation-Id";
    
    // Standard HTTP headers
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    
    private GatewayHeaders() {
        throw new UnsupportedOperationException("Utility class - cannot instantiate");
    }
}

