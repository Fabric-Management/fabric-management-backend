package com.fabricmanagement.user_service.dto.response;

import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dashboard istatistikleri
 */
public record UserDashboardStats(
        long totalUsers,
        long activeUsers,
        long verifiedUsers,
        long unverifiedUsers,
        long usersWithPassword,
        long usersWithoutPassword,
        long lockedUsers,
        long newUsersToday,
        long newUsersThisWeek,
        long newUsersThisMonth,
        List<RoleDistribution> roleDistribution,
        List<StatusDistribution> statusDistribution,
        List<DailyUserCount> dailyUserCounts,
        LocalDateTime calculatedAt
) {
    public record RoleDistribution(
            Role role,
            long count,
            double percentage
    ) {}

    public record StatusDistribution(
            UserStatus status,
            long count,
            double percentage
    ) {}

    public record DailyUserCount(
            LocalDateTime date,
            long newUsers,
            long activeUsers
    ) {}
}