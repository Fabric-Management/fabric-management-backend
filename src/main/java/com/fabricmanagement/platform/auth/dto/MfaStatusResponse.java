package com.fabricmanagement.platform.auth.dto;

import com.fabricmanagement.platform.auth.domain.MfaType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "MFA status response")
public class MfaStatusResponse {

  @Schema(description = "Whether MFA is enabled", example = "true")
  private Boolean isMfaEnabled;

  @Schema(description = "Primary MFA type", example = "TOTP")
  private MfaType primaryMfaType;

  @Schema(description = "Number of trusted devices", example = "2")
  private Integer trustedDeviceCount;
}
