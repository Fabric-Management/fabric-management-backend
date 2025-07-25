package com.fabricmanagement.user_service.dto.response;

import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        String initials,
        Set<Role> roles,
        UserStatus status,
        boolean emailVerified,
        LocalDateTime lastLoginAt
) {}
