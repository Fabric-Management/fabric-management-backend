package com.fabricmanagement.identity.application.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Single Responsibility: Forgot password request representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
public class ForgotPasswordRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;
}