package com.fabricmanagement.identity.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private String accessToken;
    private String refreshToken;
    private String username;
    private String role;
    private String tempToken;
    private String nextAction;
    private String message;

    public static LoginResponse success(String accessToken, String refreshToken, String username, String role) {
        return LoginResponse.builder()
            .success(true)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .username(username)
            .role(role)
            .message("Login successful")
            .build();
    }

    public static LoginResponse twoFactorRequired(String tempToken) {
        return LoginResponse.builder()
            .success(false)
            .tempToken(tempToken)
            .nextAction("VERIFY_2FA")
            .message("Two-factor authentication required")
            .build();
    }

    public static LoginResponse passwordChangeRequired(String tempToken) {
        return LoginResponse.builder()
            .success(false)
            .tempToken(tempToken)
            .nextAction("CHANGE_PASSWORD")
            .message("Password change required")
            .build();
    }

    public static LoginResponse failure(String message) {
        return LoginResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}