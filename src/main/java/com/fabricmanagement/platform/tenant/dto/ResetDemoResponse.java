package com.fabricmanagement.platform.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Summary returned after resetting a demo tenant with fresh sample data")
public record ResetDemoResponse(
    @Schema(description = "Tenant ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID tenantId,
    @Schema(description = "Whether the tenant remains in demo mode", example = "true")
        boolean demoMode,
    @Schema(description = "Number of persona users seeded after the reset", example = "15")
        int seededPersonaUsers,
    @Schema(description = "Rows purged by table or purge category")
        Map<String, Integer> purgedRows) {}
