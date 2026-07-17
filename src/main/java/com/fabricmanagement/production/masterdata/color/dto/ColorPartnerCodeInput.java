package com.fabricmanagement.production.masterdata.color.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Partner-owned code used to create or reactivate a color mapping")
public record ColorPartnerCodeInput(
    @Schema(
            description = "Partner's original code spelling; stored trimmed and immutable",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 50)
        @NotBlank
        @Size(max = 50)
        String externalCode,
    @Schema(
            description = "Optional partner-owned color name, already trimmed when supplied",
            nullable = true,
            maxLength = 255)
        @Size(max = 255)
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "must be null or trimmed and non-blank")
        String externalName) {}
