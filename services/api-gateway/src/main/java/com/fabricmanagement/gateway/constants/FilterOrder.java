package com.fabricmanagement.gateway.constants;

/**
 * Gateway Filter Order Constants
 * 
 * Defines execution order for GlobalFilters.
 * Lower values execute first.
 * 
 * Filter Chain:
 * 1. JWT Authentication (-100) - Validates JWT, adds security headers
 * 2. Policy Enforcement (-50)  - Authorizes request via PolicyEngine
 * 3. Request Logging (0)       - Logs request/response
 * 
 * Single source of truth - NO magic numbers!
 */
public final class FilterOrder {
    
    /**
     * JWT Authentication Filter
     * Executes FIRST - validates JWT token and adds security context headers
     */
    public static final int JWT_FILTER = -100;
    
    /**
     * Policy Enforcement Filter  
     * Executes SECOND - checks authorization via PolicyEngine
     */
    public static final int POLICY_FILTER = -50;
    
    /**
     * Request Logging Filter
     * Executes THIRD - logs all requests with correlation ID
     */
    public static final int LOGGING_FILTER = 0;
    
    private FilterOrder() {
        throw new UnsupportedOperationException("Utility class - cannot instantiate");
    }
}

