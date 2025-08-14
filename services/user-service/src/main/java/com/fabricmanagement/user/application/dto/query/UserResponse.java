package com.fabricmanagement.user.application.dto.query;

import com.fabricmanagement.user.domain.valueobject.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String username,
        String fullName,
        UserStatus status,
        UUID tenantId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}