package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * A fabric composition worked out from its yarns: each yarn's share of the mass times that yarn's
 * fibre percentages, rounded so the lines add up to exactly 100. It is a suggestion; the tenant may
 * change it before saving.
 */
@Schema(name = "FabricCompositionSuggestion")
public record FabricCompositionSuggestionDto(
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<FabricCompositionDto> composition,
    @ArraySchema(
            arraySchema =
                @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Yarn products left out because they declare no composition"))
        List<UUID> yarnsWithoutComposition,
    @ArraySchema(
            arraySchema =
                @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description =
                        "Stable codes: NO_YARNS, YARN_SHARES_INCOMPLETE (shares do not add up to"
                            + " 100, so they were scaled), YARN_COMPOSITION_MISSING"))
        List<String> problems) {}
