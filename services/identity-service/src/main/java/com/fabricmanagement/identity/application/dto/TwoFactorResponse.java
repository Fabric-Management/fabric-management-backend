package com.fabricmanagement.identity.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorResponse {
    private boolean success;
    private String accessToken;
    private String refreshToken;
    private String message;

    public static TwoFactorResponse success(String accessToken, String refreshToken) {
        return TwoFactorResponse.builder()
            .success(true)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .message("Two-factor authentication successful")
            .build();
    }

    public static TwoFactorResponse failure(String message) {
        return TwoFactorResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}