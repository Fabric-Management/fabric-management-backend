package com.fabricmanagement.identity.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Single Responsibility: Refresh token request representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
public class RefreshTokenRequest {
    
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}