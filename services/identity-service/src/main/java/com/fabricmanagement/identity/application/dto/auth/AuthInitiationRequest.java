package com.fabricmanagement.identity.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Single Responsibility: Auth initiation request representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
public class AuthInitiationRequest {
    
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    private String twoFactorCode;
}