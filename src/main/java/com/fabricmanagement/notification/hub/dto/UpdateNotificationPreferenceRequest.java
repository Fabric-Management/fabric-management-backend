package com.fabricmanagement.notification.hub.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateNotificationPreferenceRequest(
    @NotBlank String eventType, boolean inApp, boolean email, boolean push) {}
