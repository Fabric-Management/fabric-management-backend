package com.fabricmanagement.identity.application.dto.auth;

import com.fabricmanagement.identity.domain.valueobject.ContactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from authentication initiation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthInitiationResponse {

    private String nextStep; // VERIFY_CONTACT, CREATE_PASSWORD, ENTER_PASSWORD, NOT_FOUND
    private String maskedContact;
    private ContactType contactType;
    private String tempToken; // For password creation flow

    public static AuthInitiationResponse notFound() {
        return AuthInitiationResponse.builder()
            .nextStep("NOT_FOUND")
            .build();
    }

    public static AuthInitiationResponse verificationRequired(ContactType type, String maskedContact) {
        return AuthInitiationResponse.builder()
            .nextStep("VERIFY_CONTACT")
            .contactType(type)
            .maskedContact(maskedContact)
            .build();
    }

    public static AuthInitiationResponse passwordCreationRequired(String tempToken) {
        return AuthInitiationResponse.builder()
            .nextStep("CREATE_PASSWORD")
            .tempToken(tempToken)
            .build();
    }

    public static AuthInitiationResponse passwordRequired() {
        return AuthInitiationResponse.builder()
            .nextStep("ENTER_PASSWORD")
            .build();
    }
}