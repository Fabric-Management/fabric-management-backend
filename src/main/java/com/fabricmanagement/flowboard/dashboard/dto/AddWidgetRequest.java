package com.fabricmanagement.flowboard.dashboard.dto;

import com.fabricmanagement.flowboard.dashboard.domain.WidgetType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddWidgetRequest(
    @NotNull WidgetType type,
    @NotBlank String title,
    String configJsonb,
    @Min(0) int displayOrder) {}
