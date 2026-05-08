package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

@Schema(description = "Response returned after switching persona in a playground session")
public record PlaygroundImpersonateResponse(
    @Schema(
            description = "New JWT access token for the impersonated persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String token,
    @Schema(
            description = "User ID of the impersonated persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "123e4567-e89b-12d3-a456-426614174000")
        UUID userId,
    @Schema(
            description = "Display name of the impersonated persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "Jane Smith (Manager)")
        String userName,
    @Schema(
            description = "Role name of the impersonated persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "Manager")
        String role) {}
