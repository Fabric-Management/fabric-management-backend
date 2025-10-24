package com.fabricmanagement.shared.application.query;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base Query Interface
 * 
 * All queries should implement this interface to ensure consistency
 * in query handling across the system.
 */
public interface Query {
    
    /**
     * Get query ID for tracking and correlation
     * @return query ID
     */
    default UUID getQueryId() {
        return UUID.randomUUID();
    }

    /**
     * Get query timestamp
     * @return query timestamp
     */
    default LocalDateTime getQueryTimestamp() {
        return LocalDateTime.now();
    }

    /**
     * Get tenant ID for multi-tenancy support
     * @return tenant ID, can be null for system queries
     */
    default String getTenantId() {
        return null;
    }

    /**
     * Get query metadata
     * @return metadata map, can be null
     */
    default java.util.Map<String, Object> getMetadata() {
        return null;
    }
}
