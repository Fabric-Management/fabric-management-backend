package com.fabricmanagement.flowboard.routing.dto;

import com.fabricmanagement.flowboard.routing.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RoutingRepairResponse")
public record RoutingRepairResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int evaluated,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int changed,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failuresOpened,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failuresResolved) {}
