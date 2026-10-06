package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * What a save did and the base to continue from (CEDIT-02 §4.4). {@code resultVersion} is the
 * version this save left (the current version when nothing changed); {@code nextBase} is the order
 * as it is now. On a first answer the two are the same version; on a repeat the order may have
 * moved on since.
 */
@Schema(name = "SalesOrderEditResult")
public record SalesOrderEditResult(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "APPLIED or NO_CHANGE")
        SalesOrderEditOutcome outcome,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "True when this answer repeats an earlier save of the same operation")
        boolean replayed,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long resultVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SalesOrderEditLineIdMapping> lineIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesOrderEditBase nextBase) {}
