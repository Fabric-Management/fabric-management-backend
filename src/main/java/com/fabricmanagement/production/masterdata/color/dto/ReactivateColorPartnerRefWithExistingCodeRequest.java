package com.fabricmanagement.production.masterdata.color.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Reactivation branch selecting one retained inactive partner code")
public record ReactivateColorPartnerRefWithExistingCodeRequest(
    @Schema(
            description = "Inactive retained code to reactivate as the sole primary",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID existingCodeId) {}
