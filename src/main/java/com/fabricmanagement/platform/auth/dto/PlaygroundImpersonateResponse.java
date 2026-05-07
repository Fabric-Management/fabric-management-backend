package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Response returned after switching persona in a playground session")
public record PlaygroundImpersonateResponse(
    @Schema(description = "New JWT access token for the impersonated persona") String accessToken,
    @Schema(description = "User ID of the impersonated persona") UUID impersonatedUserId,
    @Schema(description = "Display name of the impersonated persona") String impersonatedName,
    @Schema(description = "Role name of the impersonated persona") String role) {}
