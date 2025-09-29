package com.fabricmanagement.user.application.port.in.command;

import com.fabricmanagement.user.application.dto.user.request.CreateUserRequest;
import com.fabricmanagement.user.application.dto.user.response.UserResponse;

/**
 * Port interface for creating a new user.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface CreateUserUseCase {
    
    /**
     * Creates a new user with the provided information.
     *
     * @param request the user creation request
     * @return the created user response
     */
    UserResponse createUser(CreateUserRequest request);
}
