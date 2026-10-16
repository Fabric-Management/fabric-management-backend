package com.fabricmanagement.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** One interface preference of the current user. */
@Schema(description = "Interface preference stored under a key")
public record UiPreferenceResponse(
    @Schema(description = "The preference key", requiredMode = Schema.RequiredMode.REQUIRED)
        String key,
    @Schema(
            description = "Ordered identifiers; empty when the preference was never saved",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> values) {}
