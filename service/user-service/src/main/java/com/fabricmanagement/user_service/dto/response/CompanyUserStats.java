package com.fabricmanagement.user_service.dto.response;

import com.fabricmanagement.user_service.entity.enums.Role;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CompanyUserStats(
        UUID companyId,
        String companyName,
        long totalUsers,
        long activeUsers,
        Map<Role, Long> usersByRole,
        LocalDateTime lastUserCreatedAt,
        LocalDateTime oldestUserCreatedAt
) {}
