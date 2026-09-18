package com.fabricmanagement.flowboard.routing.dto;

import com.fabricmanagement.flowboard.routing.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "RoutingFailureResponse")
public record RoutingFailureResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID taskId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoutingPoolKey poolKey,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoutingFailureReason reasonCode,
    UUID userId,
    Long poolRevision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.time.Instant occurredAt,
    java.time.Instant resolvedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RoutingAlertResponse> alerts) {}
