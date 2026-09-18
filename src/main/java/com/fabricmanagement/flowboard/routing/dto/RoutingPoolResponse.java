package com.fabricmanagement.flowboard.routing.dto;

import com.fabricmanagement.flowboard.routing.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "RoutingPoolResponse")
public record RoutingPoolResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoutingPoolKey poolKey,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean configured,
    @Schema(description = "Null when never configured") Long revision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RoutingMemberResponse> members) {}
