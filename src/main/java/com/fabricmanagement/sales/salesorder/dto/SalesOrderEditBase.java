package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A server edit base (CEDIT-02 §3, §4.4): its opaque id and the order read in the same consistent
 * read. The edit form is built from {@code order} and saves against {@code baseId}. A base is no
 * lock and no write right; it expires at {@code expiresAt} and is never extended.
 */
@Schema(name = "SalesOrderEditBase")
public record SalesOrderEditBase(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID baseId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long orderVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant capturedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The order with its lines and the caller's capabilities, as in the base")
        SalesOrderDto order) {}
