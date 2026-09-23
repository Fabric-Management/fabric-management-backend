package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "OrderCoverLineDecision")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderCoverLineDecision(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int lineNumber,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID evidenceId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "1")
        Long evidenceRevision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean selectable,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        OrderCoverLineBlockReason blockReason,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        OrderCoverEvidenceDto.Quantity productionQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean rationaleRequiredIfSelected) {}
