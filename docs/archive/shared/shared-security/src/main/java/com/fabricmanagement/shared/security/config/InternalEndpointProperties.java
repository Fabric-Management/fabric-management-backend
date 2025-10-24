package com.fabricmanagement.shared.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Internal Endpoints
 * 
 * Allows runtime configuration of internal endpoints via application.yml
 * 
 * This is a FALLBACK mechanism - prefer @InternalEndpoint annotation!
 * Use this only for:
 * - Legacy endpoints that can't have annotation
 * - Dynamic endpoints added at runtime
 * - Third-party library endpoints
 * 
 * @since 3.2.0 - Internal Endpoint Configuration Pattern (Oct 13, 2025)
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.internal-endpoints")
public class InternalEndpointProperties {
    
    /**
     * List of path patterns that should be treated as internal
     * 
     * Supports:
     * - Exact match: /api/v1/contacts/check-availability
     * - Prefix match: /api/v1/contacts/**
     * - Regex: /api/v1/contacts/[a-f0-9-]+/verify
     */
    private List<EndpointPattern> patterns = new ArrayList<>();
    
    @Data
    public static class EndpointPattern {
        /**
         * Path pattern (supports exact, prefix, or regex)
         */
        private String path;
        
        /**
         * HTTP method (GET, POST, PUT, DELETE, etc.)
         * If null, matches all methods
         */
        private String method;
        
        /**
         * Pattern type: EXACT, PREFIX, REGEX
         */
        private PatternType type = PatternType.PREFIX;
        
        /**
         * Description for documentation
         */
        private String description;
    }
    
    public enum PatternType {
        /**
         * Exact path match
         * Example: /api/v1/contacts/check-availability
         */
        EXACT,
        
        /**
         * Prefix match (path starts with)
         * Example: /api/v1/contacts/** matches /api/v1/contacts/anything
         */
        PREFIX,
        
        /**
         * Regex pattern match
         * Example: /api/v1/contacts/[a-f0-9-]+/verify
         */
        REGEX
    }
}

