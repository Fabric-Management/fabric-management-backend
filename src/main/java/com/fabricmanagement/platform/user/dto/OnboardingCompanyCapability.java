package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import io.swagger.v3.oas.annotations.media.Schema;

/** Company setup capability; the command always rechecks the same policy before writing. */
@Schema(name = "OnboardingCompanyCapability")
public record OnboardingCompanyCapability(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PermissionKey action,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Stable denial code; null when company setup is allowed")
        String reason,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Alternative route, if available; otherwise null")
        String routesTo,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval) {}
