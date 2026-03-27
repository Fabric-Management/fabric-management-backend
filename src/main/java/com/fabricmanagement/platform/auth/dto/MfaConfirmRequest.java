package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "MFA confirmation request")
public class MfaConfirmRequest {

  @NotBlank(message = "Verification code is required")
  @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
  @Schema(description = "6-digit verification code", example = "123456")
  private String code;
}
