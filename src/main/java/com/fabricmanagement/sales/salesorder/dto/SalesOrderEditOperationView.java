package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The recorded outcome of one of the caller's own saves, for diagnosis only. A 404 is not proof
 * that a save failed: the client repeats the same save to learn its result.
 */
@Schema(name = "SalesOrderEditOperationView")
public record SalesOrderEditOperationView(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesOrderEditOutcome outcome,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "The version the save left; null for a conflict")
        Long resultVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SalesOrderEditLineIdMapping> lineIds,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "The base a conflict was answered with; null otherwise")
        UUID conflictBaseId) {}
