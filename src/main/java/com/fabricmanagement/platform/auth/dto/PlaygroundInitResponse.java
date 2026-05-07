package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Response returned after a new playground session is initialized")
public record PlaygroundInitResponse(
    @Schema(description = "Unique identifier for the guest session") String guestId,
    @Schema(description = "JWT access token for the playground session") String accessToken,
    @Schema(description = "ID of the newly created playground tenant") UUID tenantId,
    @Schema(description = "User ID of the default persona") UUID impersonatedUserId,
    @Schema(description = "Display name of the default persona") String impersonatedName,
    @Schema(description = "Role name of the default persona") String role) {}
