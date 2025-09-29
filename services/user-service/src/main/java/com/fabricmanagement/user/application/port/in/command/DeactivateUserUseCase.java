package com.fabricmanagement.user.application.port.in.command;

import java.util.UUID;

/**
 * Use case interface for deactivating a user profile.
 * Single responsibility: Handle user profile deactivation.
 */
public interface DeactivateUserUseCase {
    
    /**
     * Deactivates a user profile.
     * 
     * @param userId the user ID
     */
    void deactivateUser(UUID userId);
}
