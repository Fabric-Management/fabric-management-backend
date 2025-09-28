package com.fabricmanagement.identity.application.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for authentication operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private String userId;
    private String username;
    private String email;
    private String role;
    private boolean twoFactorRequired;
    private boolean passwordChangeRequired;
    private LocalDateTime expiresAt;
}
