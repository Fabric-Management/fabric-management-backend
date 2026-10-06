package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A line a save added: the client's stable id and the line id it became. */
@Schema(name = "SalesOrderEditLineIdMapping")
public record SalesOrderEditLineIdMapping(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID clientLineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId) {}
