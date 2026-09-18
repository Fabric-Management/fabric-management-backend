package com.fabricmanagement.flowboard.routing.dto;

import com.fabricmanagement.flowboard.routing.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "RoutingAlertResponse")
public record RoutingAlertResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID recipientId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"IN_APP"})
        String channel,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"PENDING", "FAILED", "DELIVERED", "CANCELLED"})
        String status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attempts,
    String lastError) {}
