package com.fabricmanagement.platform.admin.dto;

import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One tenant as the platform screen lists it (PLATFORM-BOUNDARY-1). */
@Schema(description = "A tenant as the platform staff screen lists it")
public record PlatformTenantSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uid,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "What the tenant is")
        PlatformTenantKind kind,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The screen section the tenant belongs to")
        PlatformTenantGroup group,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TenantStatus status,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Whether the company is trying FabricOS with sample data")
        boolean demoMode,
    @Schema(description = "When the trial ends; absent when there is no trial") Instant trialEndsAt,
    @Schema(description = "Last recorded activity; absent when none") Instant lastActivityAt,
    @Schema(description = "When the tenant was created") Instant createdAt) {}
