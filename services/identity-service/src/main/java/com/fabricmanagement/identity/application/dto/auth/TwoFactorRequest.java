package com.fabricmanagement.identity.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Single Responsibility: Two-factor request representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
public class TwoFactorRequest {
    
    @NotBlank(message = "Two-factor code is required")
    private String code;
    
    private String userId;
}