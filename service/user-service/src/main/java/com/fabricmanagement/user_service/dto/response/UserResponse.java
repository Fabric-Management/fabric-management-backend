package com.fabricmanagement.user_service.dto.response;

import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String displayName,
        String initials,
        Set<Role> roles,
        UserStatus status,
        boolean emailVerified,
        boolean hasPassword,
        UUID companyId,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordChangedAt,
        String accountStatusMessage,
        boolean canLogin
) {
    // Factory method for creating from entity will be in mapper
}