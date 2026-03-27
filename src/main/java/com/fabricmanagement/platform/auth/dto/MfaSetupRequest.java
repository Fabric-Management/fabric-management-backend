package com.fabricmanagement.platform.auth.dto;

import com.fabricmanagement.platform.auth.domain.MfaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "MFA setup request")
public class MfaSetupRequest {

  @NotNull(message = "MFA type is required")
  @Schema(description = "MFA type to enable", example = "TOTP")
  private MfaType mfaType;
}
