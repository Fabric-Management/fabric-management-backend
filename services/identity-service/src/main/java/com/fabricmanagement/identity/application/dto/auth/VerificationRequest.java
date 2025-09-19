package com.fabricmanagement.identity.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to verify contact with code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    @NotBlank(message = "Contact is required")
    private String contact;

    @NotBlank(message = "Verification code is required")
    private String code; // 6-digit code for SMS or token for email
}