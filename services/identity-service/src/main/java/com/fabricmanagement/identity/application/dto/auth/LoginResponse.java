package com.fabricmanagement.identity.application.dto.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Single Responsibility: Login response representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
public class LoginResponse {
    
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private LocalDateTime expiresAt;
    private boolean requiresTwoFactor;
    private String userId;
    private String username;
    private String email;
    private String role;
}