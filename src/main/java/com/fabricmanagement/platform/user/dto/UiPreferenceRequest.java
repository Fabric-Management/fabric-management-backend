package com.fabricmanagement.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The values to keep under one interface preference key; they replace what was stored. */
@Schema(description = "Interface preference values to store under a key")
public record UiPreferenceRequest(
    @Schema(
            description =
                "Ordered identifiers chosen by the screen that owns the key, at most 50, each 1-100 "
                    + "characters and unique. An empty list clears the preference.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Size(max = 50)
        List<String> values) {}
