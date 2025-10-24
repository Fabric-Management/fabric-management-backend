package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Security Constants
 * 
 * Centralized security-related constants following PRINCIPLES.md
 */
public final class SecurityConstants {
    
    private SecurityConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // JWT Constants
    public static final String JWT_HEADER = "Authorization";
    public static final String JWT_PREFIX = "Bearer ";
    public static final String JWT_CLAIM_TENANT_ID = "tenantId";
    public static final String JWT_CLAIM_USER_ID = "userId";
    public static final String JWT_CLAIM_ROLES = "roles";
    
    // Role Constants (NO HARDCODED STRINGS!)
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_COMPANY_MANAGER = "COMPANY_MANAGER";
    public static final String ROLE_DEPARTMENT_MANAGER = "DEPARTMENT_MANAGER";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_CEO = "CEO";
    public static final String ROLE_PURCHASER = "PURCHASER";
    public static final String ROLE_SALES = "SALES";
    public static final String ROLE_PRODUCTION = "PRODUCTION";
    public static final String ROLE_INTERNAL_SERVICE = "ROLE_INTERNAL_SERVICE"; // For service-to-service calls

    // Internal Service Authentication (NO HARDCODED STRINGS!)
    public static final String INTERNAL_SERVICE_PRINCIPAL = "internal-service";
    public static final String INTERNAL_SERVICE_DISPLAY_NAME = "Internal Service";
    public static final String INTERNAL_SERVICE_DESCRIPTION = "Authenticated internal service-to-service call";
    
    // Password Constants
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 128;
    public static final int BCRYPT_STRENGTH = 12;
    
    // Token Expiration (in milliseconds)
    public static final long ACCESS_TOKEN_EXPIRATION = 3600000L; // 1 hour
    public static final long REFRESH_TOKEN_EXPIRATION = 2592000000L; // 30 days
    public static final long PASSWORD_RESET_TOKEN_EXPIRATION = 900000L; // 15 minutes
    
    // Rate Limiting
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final long LOGIN_LOCKOUT_DURATION = 900000L; // 15 minutes
    
    // Session Constants
    public static final long SESSION_TIMEOUT = 3600000L; // 1 hour
    public static final int MAX_CONCURRENT_SESSIONS = 3;
    
    // Error Messages (NO HARDCODED STRINGS!)
    public static final String MSG_INSUFFICIENT_PERMISSIONS = "Insufficient permissions";
    public static final String MSG_UNAUTHORIZED_ACCESS = "Unauthorized access";
    public static final String MSG_FORBIDDEN = "Access forbidden";
    
    // Error Codes (NO HARDCODED STRINGS!)
    public static final String ERROR_CODE_FORBIDDEN = "FORBIDDEN";
    public static final String ERROR_CODE_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_CODE_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
}

