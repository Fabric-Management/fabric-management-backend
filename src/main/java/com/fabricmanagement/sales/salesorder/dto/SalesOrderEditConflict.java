package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * One key or line that was not saved (CEDIT-02 §5.6). Values have the key's own shape; a whole line
 * is shown as its projection. Values are returned only to a caller allowed to write the order.
 */
@Schema(name = "SalesOrderEditConflict")
public record SalesOrderEditConflict(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Edit key, for example paymentTerms or line.pricing; line for a whole line")
        String key,
    @Schema(description = "The existing line the conflict is about") UUID lineId,
    @Schema(description = "The new line the conflict is about") UUID clientLineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesOrderEditConflictReason reason,
    @Schema(description = "The value in the base the save was made against") JsonNode base,
    @Schema(description = "The value saved now") JsonNode current,
    @Schema(description = "The value the save asked for") JsonNode mine,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SalesOrderEditResolutionChoice> choices) {}
