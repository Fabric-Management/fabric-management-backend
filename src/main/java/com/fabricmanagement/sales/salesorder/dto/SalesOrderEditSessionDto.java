package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** My open edit session of one order (CEDIT-06 §3). */
@Schema(
    name = "SalesOrderEditSessionDto",
    description =
        "An edit session of this browser tab: presence only, never a lock or a write right. Renew"
            + " it after renewAfterSeconds while the edit form stays open; close it when the form"
            + " closes. A session that is not renewed ends at expiresAt.")
public record SalesOrderEditSessionDto(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "uuid",
            description = "Server-generated id of this session; each opening has a new one.")
        UUID editSessionId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "When the session ends unless it is renewed before.")
        Instant expiresAt,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1",
            example = "30",
            description = "Seconds after which the client renews the session.")
        long renewAfterSeconds) {}
