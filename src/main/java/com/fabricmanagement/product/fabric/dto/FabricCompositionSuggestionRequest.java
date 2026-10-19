package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The yarns to derive a fabric composition from; the fabric itself need not exist yet. */
@Schema(name = "FabricCompositionSuggestionRequest")
public record FabricCompositionSuggestionRequest(
    @NotNull
        @Size(max = 20)
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<@Valid @NotNull FabricYarnRequest> yarns) {}
