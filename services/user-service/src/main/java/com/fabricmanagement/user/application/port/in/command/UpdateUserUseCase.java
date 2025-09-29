package com.fabricmanagement.user.application.port.in.command;

import com.fabricmanagement.user.application.dto.user.request.UpdateUserRequest;
import com.fabricmanagement.user.application.dto.user.response.UserResponse;
import com.fabricmanagement.user.domain.valueobject.UserId;

/**
 * Port interface for updating an existing user.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface UpdateUserUseCase {
    
    /**
     * Updates an existing user with the provided information.
     *
     * @param userId the user ID to update
     * @param request the user update request
     * @return the updated user response
     */
    UserResponse updateUser(UserId userId, UpdateUserRequest request);
}
