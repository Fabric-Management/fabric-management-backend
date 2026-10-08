package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One open edit form of the order: who holds it, since when (CEDIT-06 §3). */
@Schema(
    name = "SalesOrderEditorDto",
    description =
        "One open edit session of the order. Another person's session id is never shown; mine"
            + " tells the caller's own sessions apart (another tab of the same person).")
public record SalesOrderEditorDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID userId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "The person's display name; null when the account has none.")
        String displayName,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "When this edit form was opened.")
        Instant openedAt,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Whether the session belongs to the caller.")
        boolean mine,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            format = "uuid",
            description = "The session id, only for the caller's own sessions; otherwise null.")
        UUID editSessionId) {}
