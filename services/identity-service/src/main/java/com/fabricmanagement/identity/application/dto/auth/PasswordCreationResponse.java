package com.fabricmanagement.identity.application.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from password creation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordCreationResponse {

    private String accessToken;
    private String refreshToken;
    private String fullName;

    public static PasswordCreationResponse success(String accessToken, String refreshToken, String fullName) {
        return PasswordCreationResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .fullName(fullName)
            .build();
    }
}