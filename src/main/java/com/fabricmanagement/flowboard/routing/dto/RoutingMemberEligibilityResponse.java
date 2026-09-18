package com.fabricmanagement.flowboard.routing.dto;

import com.fabricmanagement.flowboard.routing.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "RoutingMemberEligibilityResponse")
public record RoutingMemberEligibilityResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean eligible,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RoutingReason> reasons) {}
