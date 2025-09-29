package com.fabricmanagement.user.application.port.in.command;

import com.fabricmanagement.user.application.dto.user.request.CreateUserRequest;
import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;

/**
 * Use case interface for creating a user profile.
 * Single responsibility: Handle user profile creation.
 */
public interface CreateUserUseCase {
    
    /**
     * Creates a new user profile.
     * 
     * @param request the user creation request
     * @return the created user details
     */
    UserDetailResponse createUser(CreateUserRequest request);
}