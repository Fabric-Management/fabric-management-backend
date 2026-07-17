package com.fabricmanagement.production.masterdata.color.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Reactivation branch creating one new active primary partner code")
public record ReactivateColorPartnerRefWithNewCodeRequest(
    @Schema(
            description = "New code to create as the sole primary",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Valid
        ColorPartnerCodeInput newPrimaryCode) {}
