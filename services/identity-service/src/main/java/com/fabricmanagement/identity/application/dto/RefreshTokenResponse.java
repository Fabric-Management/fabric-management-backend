package com.fabricmanagement.identity.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenResponse {
    private boolean success;
    private String accessToken;
    private String message;

    public static RefreshTokenResponse success(String accessToken) {
        return RefreshTokenResponse.builder()
            .success(true)
            .accessToken(accessToken)
            .message("Token refreshed successfully")
            .build();
    }

    public static RefreshTokenResponse failure(String message) {
        return RefreshTokenResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}