package com.fabricmanagement.identity.application.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from contact verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationResponse {

    private boolean verified;
    private String tempToken; // For password creation if needed
    private boolean passwordCreationRequired;

    public static VerificationResponse success() {
        return VerificationResponse.builder()
            .verified(true)
            .passwordCreationRequired(false)
            .build();
    }

    public static VerificationResponse successWithPasswordCreation(String tempToken) {
        return VerificationResponse.builder()
            .verified(true)
            .passwordCreationRequired(true)
            .tempToken(tempToken)
            .build();
    }
}