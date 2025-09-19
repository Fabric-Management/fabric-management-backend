package com.fabricmanagement.identity.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {
    private boolean success;
    private String message;
    private String tempToken;
    private String nextAction;

    public static VerificationResponse success() {
        return VerificationResponse.builder()
            .success(true)
            .message("Contact verified successfully")
            .build();
    }

    public static VerificationResponse successWithPasswordCreation(String tempToken) {
        return VerificationResponse.builder()
            .success(true)
            .message("Contact verified. Please create your password")
            .tempToken(tempToken)
            .nextAction("CREATE_PASSWORD")
            .build();
    }

    public static VerificationResponse failure(String message) {
        return VerificationResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}