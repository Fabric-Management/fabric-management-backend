package com.fabricmanagement.gateway.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * UUID Validator
 * 
 * Validates UUID format and provides safe parsing.
 * Used by filters to validate UUIDs from JWT claims.
 * 
 * Security: First line of defense against malformed UUIDs.
 */
@Component
public class UuidValidator {
    
    /**
     * Validates UUID format
     * 
     * @param uuid String to validate
     * @return true if valid UUID format, false otherwise
     */
    public boolean isValid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Parses UUID string safely
     * 
     * @param uuid String to parse
     * @return UUID object or null if invalid
     */
    public UUID parseOrNull(String uuid) {
        if (!isValid(uuid)) {
            return null;
        }
        
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

