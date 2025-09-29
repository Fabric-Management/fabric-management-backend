package com.fabricmanagement.user.application.port.in.command;

import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;

import java.util.UUID;

/**
 * Use case interface for activating a user profile.
 * Single responsibility: Handle user profile activation.
 */
public interface ActivateUserUseCase {
    
    /**
     * Activates a user profile.
     * 
     * @param userId the user ID
     * @return the activated user details
     */
    UserDetailResponse activateUser(UUID userId);
}
