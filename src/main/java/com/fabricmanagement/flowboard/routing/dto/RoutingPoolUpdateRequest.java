package com.fabricmanagement.flowboard.routing.dto;

import com.fabricmanagement.flowboard.routing.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "RoutingPoolUpdateRequest")
public record RoutingPoolUpdateRequest(
    @Schema(description = "Absent only when configuring the pool for the first time")
        @jakarta.validation.constraints.Positive
        Long expectedRevision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @jakarta.validation.constraints.NotNull
        java.util.Set<@jakarta.validation.constraints.NotNull UUID> memberUserIds) {}
