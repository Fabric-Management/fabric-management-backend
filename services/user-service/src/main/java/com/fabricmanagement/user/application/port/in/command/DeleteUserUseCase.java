package com.fabricmanagement.user.application.port.in.command;

import java.util.UUID;

/**
 * Use case interface for deleting a user profile.
 * Single responsibility: Handle user profile deletion.
 */
public interface DeleteUserUseCase {
    
    /**
     * Permanently deletes a user profile.
     * 
     * @param userId the user ID
     */
    void deleteUser(UUID userId);
}
