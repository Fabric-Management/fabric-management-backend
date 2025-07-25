package com.fabricmanagement.user_service.dto.response;

import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
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
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordChangedAt,
        String greeting,
        String lastLoginText,
        String accountStatusMessage,
        UserStatistics statistics
) {}
