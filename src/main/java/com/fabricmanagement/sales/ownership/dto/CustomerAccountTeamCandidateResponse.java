package com.fabricmanagement.sales.ownership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record CustomerAccountTeamCandidateResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
    @Schema(nullable = true) String displayName) {}
