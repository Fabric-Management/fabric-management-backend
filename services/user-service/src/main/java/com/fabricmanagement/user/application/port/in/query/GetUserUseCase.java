package com.fabricmanagement.user.application.port.in.query;

import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;

import java.util.UUID;

/**
 * Use case interface for getting a user profile.
 * Single responsibility: Handle user profile retrieval.
 */
public interface GetUserUseCase {
    
    /**
     * Gets a user profile by ID.
     * 
     * @param userId the user ID
     * @return the user details
     */
    UserDetailResponse getUserById(UUID userId);
}
