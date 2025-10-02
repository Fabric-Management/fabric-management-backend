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
}

