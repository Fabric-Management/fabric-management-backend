package com.fabricmanagement.user_service.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LoginActivityStats(
        long totalLoginsToday,
        long uniqueUsersToday,
        long failedLoginAttempts,
        List<HourlyLoginCount> hourlyDistribution,
        List<TopActiveUser> topActiveUsers,
        double averageLoginsPerUser
) {
    public record HourlyLoginCount(
            int hour,
            long loginCount
    ) {}

    public record TopActiveUser(
            UUID userId,
            String displayName,
            long loginCount,
            LocalDateTime lastLoginAt
    ) {}
}
