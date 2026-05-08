package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

@Schema(description = "Response returned after a new playground session is initialized")
public record PlaygroundInitResponse(
    @Schema(
            description = "Unique identifier for the guest session",
            requiredMode = RequiredMode.REQUIRED,
            example = "demo-guest-42")
        String guestId,
    @Schema(
            description = "JWT access token for the playground session",
            requiredMode = RequiredMode.REQUIRED,
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String token,
    @Schema(
            description = "ID of the newly created playground tenant",
            requiredMode = RequiredMode.REQUIRED,
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID tenantId,
    @Schema(
            description = "User ID of the default persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "123e4567-e89b-12d3-a456-426614174000")
        UUID userId,
    @Schema(
            description = "Display name of the default persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "John Doe (Admin)")
        String userName,
    @Schema(
            description = "Role name of the default persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "Platform Admin")
        String role,
    @Schema(
            description = "Organization type of the playground tenant",
            requiredMode = RequiredMode.REQUIRED,
            example = "VERTICAL_MILL")
        String organizationType) {}
