package com.fabricmanagement.shared.application.command;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base Command Interface
 * 
 * All commands should implement this interface to ensure consistency
 * in command handling across the system.
 */
public interface Command {
    
    /**
     * Get command ID for tracking and correlation
     * @return command ID
     */
    default UUID getCommandId() {
        return UUID.randomUUID();
    }

    /**
     * Get command timestamp
     * @return command timestamp
     */
    default LocalDateTime getCommandTimestamp() {
        return LocalDateTime.now();
    }

    /**
     * Get tenant ID for multi-tenancy support
     * @return tenant ID, can be null for system commands
     */
    default String getTenantId() {
        return null;
    }

    /**
     * Get command metadata
     * @return metadata map, can be null
     */
    default java.util.Map<String, Object> getMetadata() {
        return null;
    }
}
