package com.fabricmanagement.identity.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetResponse {
    private boolean success;
    private String maskedContact;
    private String contactType;
    private String message;

    public static PasswordResetResponse success(String maskedContact, String contactType) {
        return PasswordResetResponse.builder()
            .success(true)
            .maskedContact(maskedContact)
            .contactType(contactType)
            .message("Password reset instructions sent")
            .build();
    }

    public static PasswordResetResponse failure(String message) {
        return PasswordResetResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}