package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Token Constants
 * 
 * Centralized token-related constants for consistent token management
 * across all services. These are default values that can be overridden
 * via application configuration.
 */
public final class TokenConstants {
    
    private TokenConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // Password Reset Token Constants
    public static final int DEFAULT_RESET_TOKEN_EXPIRY_MINUTES = 15;
    public static final int DEFAULT_RESET_TOKEN_ATTEMPTS = 3;
    public static final int RESET_TOKEN_CODE_LENGTH = 6;
    
    // Verification Token Constants
    public static final int DEFAULT_VERIFICATION_TOKEN_EXPIRY_MINUTES = 15;
    public static final int DEFAULT_VERIFICATION_TOKEN_ATTEMPTS = 5;
    public static final int VERIFICATION_CODE_LENGTH = 6;
    
    // Invitation Token Constants
    public static final int DEFAULT_INVITATION_TOKEN_EXPIRY_DAYS = 7;
    
    // Token Generation Constants
    public static final int MIN_RANDOM_CODE = 100000;
    public static final int MAX_RANDOM_CODE = 999999;
}

