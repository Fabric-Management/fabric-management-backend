package com.fabricmanagement.production.masterdata.color.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Full mutable state of a partner code; its code identity is immutable")
public record UpdateColorPartnerCodeRequest(
    @Schema(
            description = "Optional partner-owned color name; null clears it",
            nullable = true,
            maxLength = 255)
        @Size(max = 255)
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "must be null or trimmed and non-blank")
        String externalName) {}
