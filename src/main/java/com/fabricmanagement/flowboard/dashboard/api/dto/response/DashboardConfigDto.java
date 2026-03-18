package com.fabricmanagement.flowboard.dashboard.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DashboardConfigDto(
    UUID id,
    UUID userId,
    String name,
    boolean isDefault,
    String layoutJsonb,
    Instant createdAt,
    Instant updatedAt) {}
