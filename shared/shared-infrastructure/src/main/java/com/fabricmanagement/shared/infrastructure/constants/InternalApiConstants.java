package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Internal API Communication Constants
 * 
 * Used for secure service-to-service communication.
 * 
 * SECURITY: Internal API key is used to authenticate inter-service calls.
 * This ensures that even on internal Docker network, services verify each other.
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
public final class InternalApiConstants {
    
    private InternalApiConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Header name for internal service authentication
     */
    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";
    
    /**
     * Environment variable name for internal API key
     * MUST be set in docker-compose.yml and .env
     */
    public static final String INTERNAL_API_KEY_ENV = "INTERNAL_API_KEY";
    
    /**
     * Error messages
     */
    public static final String MSG_MISSING_INTERNAL_KEY = "Internal API key is missing or invalid";
    public static final String MSG_INVALID_INTERNAL_KEY = "Invalid internal API key";
}

