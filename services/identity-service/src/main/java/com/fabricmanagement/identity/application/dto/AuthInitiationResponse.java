package com.fabricmanagement.identity.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthInitiationResponse {
    private String status;
    private String action;
    private String contactType;
    private String maskedContact;
    private String tempToken;
    private String message;

    public static AuthInitiationResponse notFound() {
        return AuthInitiationResponse.builder()
            .status("NOT_FOUND")
            .message("Contact not found")
            .build();
    }

    public static AuthInitiationResponse verificationRequired(String contactType, String maskedContact) {
        return AuthInitiationResponse.builder()
            .status("VERIFICATION_REQUIRED")
            .action("VERIFY_CONTACT")
            .contactType(contactType)
            .maskedContact(maskedContact)
            .message("Verification code sent")
            .build();
    }

    public static AuthInitiationResponse passwordCreationRequired(String tempToken) {
        return AuthInitiationResponse.builder()
            .status("PASSWORD_CREATION_REQUIRED")
            .action("CREATE_PASSWORD")
            .tempToken(tempToken)
            .message("Please create your password")
            .build();
    }

    public static AuthInitiationResponse passwordRequired() {
        return AuthInitiationResponse.builder()
            .status("PASSWORD_REQUIRED")
            .action("ENTER_PASSWORD")
            .message("Please enter your password")
            .build();
    }
}