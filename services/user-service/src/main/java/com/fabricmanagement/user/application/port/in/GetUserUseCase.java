package com.fabricmanagement.user.application.port.in;

import com.fabricmanagement.user.application.dto.query.UserResponse;

import java.util.UUID;

public interface GetUserUseCase {
    UserResponse getUserById(UUID userId, UUID tenantId);
}