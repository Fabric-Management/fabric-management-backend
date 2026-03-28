package com.fabricmanagement.flowboard.dashboard.dto;

import com.fabricmanagement.flowboard.dashboard.domain.WidgetType;
import java.time.Instant;
import java.util.UUID;

public record DashboardWidgetDto(
    UUID id,
    UUID dashboardId,
    WidgetType widgetType,
    String title,
    String configJsonb,
    int displayOrder,
    Instant createdAt,
    Instant updatedAt) {}
