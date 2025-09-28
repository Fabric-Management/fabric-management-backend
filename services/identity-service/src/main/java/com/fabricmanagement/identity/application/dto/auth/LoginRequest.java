package com.fabricmanagement.identity.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user login.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Contact value is required")
    private String contactValue; // Can be email or phone

    @NotBlank(message = "Password is required")
    private String password;

    private String twoFactorCode; // Optional for 2FA
}