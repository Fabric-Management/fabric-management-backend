package com.fabricmanagement.sales.ownership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record CustomerAccountTeamMemberResponse(
    UUID userId, @Schema(nullable = true) String displayName, boolean active, Instant createdAt) {}
