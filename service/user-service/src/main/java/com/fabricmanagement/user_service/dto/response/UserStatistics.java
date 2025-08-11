package com.fabricmanagement.user_service.dto.response;

import java.time.LocalDateTime;

public record UserStatistics(
        long totalLogins,
        long activeDays,
        LocalDateTime memberSince,
        String memberSinceText
) {}
