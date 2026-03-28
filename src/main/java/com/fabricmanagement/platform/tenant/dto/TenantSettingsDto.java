package com.fabricmanagement.platform.tenant.dto;

import java.io.Serializable;

public record TenantSettingsDto(
    String timezone,
    String locale,
    String currency,
    String country,
    boolean betaFeaturesEnabled,
    boolean aiEnabled,
    boolean emailNotificationsEnabled,
    String logoUrl,
    String primaryColor,
    boolean mfaRequired,
    int sessionTimeoutMinutes,
    String[] allowedIpRanges)
    implements Serializable {}
