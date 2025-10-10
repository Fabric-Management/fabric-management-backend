package com.fabricmanagement.gateway.util;

import com.fabricmanagement.gateway.constants.GatewayHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * JWT Token Extractor
 * 
 * Extracts JWT token from Authorization header.
 * Handles Bearer prefix removal.
 */
@Component
public class JwtTokenExtractor {
    
    /**
     * Extract JWT token from request
     * 
     * @param request ServerHttpRequest
     * @return JWT token string (without Bearer prefix) or null if not found
     */
    public String extract(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null) {
            return null;
        }
        
        if (authHeader.startsWith(GatewayHeaders.BEARER_PREFIX)) {
            return authHeader.substring(GatewayHeaders.BEARER_PREFIX.length());
        }
        
        return null;
    }
}

