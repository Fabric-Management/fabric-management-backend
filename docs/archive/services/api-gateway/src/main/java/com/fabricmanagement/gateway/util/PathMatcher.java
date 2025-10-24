package com.fabricmanagement.gateway.util;

import com.fabricmanagement.gateway.constants.GatewayPaths;
import org.springframework.stereotype.Component;

/**
 * Path Matcher
 * 
 * Provides path matching utilities for filters.
 * Determines if a path is public or protected.
 */
@Component
public class PathMatcher {
    
    /**
     * Check if path is public endpoint
     * 
     * @param path Request path
     * @return true if public (no auth required), false if protected
     */
    public boolean isPublic(String path) {
        return GatewayPaths.isPublic(path);
    }
}

