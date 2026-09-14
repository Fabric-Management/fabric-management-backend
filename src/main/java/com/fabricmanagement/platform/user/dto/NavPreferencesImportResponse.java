package com.fabricmanagement.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result of atomically importing local preferences without replacing an existing account row. */
@Schema(description = "Atomic navigation preference import result")
public record NavPreferencesImportResponse(
    @Schema(
            description = "True only when this request created the account's preferences",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean imported,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) NavPreferencesResponse preferences) {}
