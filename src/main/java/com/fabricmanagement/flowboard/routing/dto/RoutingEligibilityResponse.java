package com.fabricmanagement.flowboard.routing.dto;

import com.fabricmanagement.flowboard.routing.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "RoutingEligibilityResponse")
public record RoutingEligibilityResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID taskId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<RoutingMemberEligibilityResponse> members) {}
