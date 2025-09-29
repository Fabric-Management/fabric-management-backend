package com.fabricmanagement.user.application.port.in.command;

import com.fabricmanagement.user.application.dto.user.request.UpdateUserRequest;
import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;

import java.util.UUID;

/**
 * Use case interface for updating a user profile.
 * Single responsibility: Handle user profile updates.
 */
public interface UpdateUserUseCase {
    
    /**
     * Updates an existing user profile.
     * 
     * @param userId the user ID
     * @param request the user update request
     * @return the updated user details
     */
    UserDetailResponse updateUser(UUID userId, UpdateUserRequest request);
}