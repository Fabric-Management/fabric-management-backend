package com.fabricmanagement.identity.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to initiate authentication with email or phone.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthInitiationRequest {

    @NotBlank(message = "Contact is required")
    private String contact; // Email or phone number
}