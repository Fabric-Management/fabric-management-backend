package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Available persona for playground impersonation")
public record PlaygroundPersonaDto(
    @Schema(description = "User ID") UUID id,
    @Schema(description = "Display name of the persona") String name,
    @Schema(description = "Role name") String role,
    @Schema(description = "Primary department name") String department) {}
