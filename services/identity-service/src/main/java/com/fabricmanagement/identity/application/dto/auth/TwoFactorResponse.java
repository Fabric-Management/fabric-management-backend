package com.fabricmanagement.identity.application.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorResponse {

    private String accessToken;
    private String refreshToken;

    public static TwoFactorResponse success(String accessToken, String refreshToken) {
        return TwoFactorResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
    }
}