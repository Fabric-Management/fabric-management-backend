package com.fabricmanagement.analytics.dto;

import lombok.Builder;

@Builder
public record RevenueBacklogWarningDto(String code, String referenceId, String message) {}
