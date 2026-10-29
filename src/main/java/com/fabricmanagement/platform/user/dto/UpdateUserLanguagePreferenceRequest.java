package com.fabricmanagement.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Changes only the authenticated person's language; their saved timezone is untouched. */
@Schema(description = "Change the authenticated user's language without changing their timezone")
public record UpdateUserLanguagePreferenceRequest(
    @Schema(
            description = "Supported IETF BCP 47 language tag; the saved timezone is preserved",
            example = "en-GB",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(
            regexp = "^([eE][nN]|[tT][rR])(-[a-zA-Z]{2,4})?$",
            message = "Language must be an English or Turkish BCP 47 tag")
        String preferredLocale) {}
