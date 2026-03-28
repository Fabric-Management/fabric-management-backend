package com.fabricmanagement.platform.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantSettingsRequest(
    @NotBlank @Size(max = 50) String timezone,
    @NotBlank @Size(max = 20) String locale,
    @NotBlank @Size(min = 3, max = 3) String currency,
    @Size(min = 2, max = 2) String country,
    boolean betaFeaturesEnabled,
    boolean aiEnabled,
    boolean emailNotificationsEnabled,
    @Size(max = 255) String logoUrl,
    @Size(max = 7) String primaryColor,
    boolean mfaRequired,
    @Min(15) @Max(10080) int sessionTimeoutMinutes,
    String[] allowedIpRanges) {}
