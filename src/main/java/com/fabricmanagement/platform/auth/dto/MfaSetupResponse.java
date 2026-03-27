package com.fabricmanagement.platform.auth.dto;

import com.fabricmanagement.platform.auth.domain.MfaType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "MFA setup response")
public class MfaSetupResponse {

  @Schema(description = "MFA type", example = "TOTP")
  private MfaType mfaType;

  @Schema(description = "TOTP secret (only for TOTP)", example = "JBSWY3DPEHPK3PXP")
  private String secret;

  @Schema(
      description = "QR code URI for TOTP (only for TOTP)",
      example = "otpauth://totp/FabricManagement:user@example.com?secret=JBSWY3DPEHPK3PXP")
  private String qrCodeUri;

  @Schema(description = "Setup message", example = "TOTP setup initiated. Scan QR code.")
  private String message;
}
