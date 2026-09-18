package com.fabricmanagement.flowboard.routing.dto;

import com.fabricmanagement.flowboard.routing.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "RoutingMemberResponse")
public record RoutingMemberResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
    @Schema(description = "Current display name, if the user is visible") String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean candidate,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RoutingReason> candidacyReasons) {}
