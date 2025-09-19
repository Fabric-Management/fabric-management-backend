package com.fabricmanagement.identity.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordCreationResponse {
    private boolean success;
    private String accessToken;
    private String refreshToken;
    private String username;
    private String message;

    public static PasswordCreationResponse success(String accessToken, String refreshToken, String username) {
        return PasswordCreationResponse.builder()
            .success(true)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .username(username)
            .message("Password created successfully")
            .build();
    }

    public static PasswordCreationResponse failure(String message) {
        return PasswordCreationResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}