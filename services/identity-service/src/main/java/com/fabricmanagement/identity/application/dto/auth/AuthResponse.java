package com.fabricmanagement.identity.application.dto.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Single Responsibility: Auth response representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
public class AuthResponse {
    
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private String userId;
    private String username;
    private String email;
    private String role;
}