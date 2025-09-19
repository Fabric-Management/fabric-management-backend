package com.fabricmanagement.identity.application.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from login.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String fullName;
    private String role;
    private boolean twoFactorRequired;
    private String tempToken; // For 2FA or password change
    private boolean passwordChangeRequired;

    public static LoginResponse success(String accessToken, String refreshToken, String fullName, String role) {
        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .fullName(fullName)
            .role(role)
            .twoFactorRequired(false)
            .passwordChangeRequired(false)
            .build();
    }

    public static LoginResponse twoFactorRequired(String tempToken) {
        return LoginResponse.builder()
            .twoFactorRequired(true)
            .tempToken(tempToken)
            .build();
    }

    public static LoginResponse passwordChangeRequired(String tempToken) {
        return LoginResponse.builder()
            .passwordChangeRequired(true)
            .tempToken(tempToken)
            .build();
    }
}