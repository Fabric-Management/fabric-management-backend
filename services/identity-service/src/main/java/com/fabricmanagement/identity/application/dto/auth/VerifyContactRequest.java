package com.fabricmanagement.identity.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for contact verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyContactRequest {

    @NotBlank(message = "Contact value is required")
    private String contactValue;

    @NotBlank(message = "Verification code is required")
    private String verificationCode;
}
