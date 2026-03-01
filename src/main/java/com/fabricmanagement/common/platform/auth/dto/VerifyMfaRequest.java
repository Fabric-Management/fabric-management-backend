package com.fabricmanagement.common.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyMfaRequest {

  @NotBlank(message = "MFA Token is required")
  private String mfaToken;

  @NotBlank(message = "Verification code is required")
  private String code;

  @Builder.Default private Boolean rememberDevice = false;
}
