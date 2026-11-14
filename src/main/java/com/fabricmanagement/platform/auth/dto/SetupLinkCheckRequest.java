package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Asks whether a setup link can still be used. The token is in the body, never in the URL. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupLinkCheckRequest {

  @Schema(
      description = "The token from the setup link",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "Token is required")
  private String token;
}
