package com.fabricmanagement.contact.application.dto.contact.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating a new contact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContactRequest {

    // Basic fields
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 200, message = "Display name must not exceed 200 characters")
    private String displayName;

    // User-specific fields
    @Size(max = 100, message = "Job title must not exceed 100 characters")
    private String jobTitle;

    @Size(max = 100, message = "Department must not exceed 100 characters")
    private String department;

    @Size(max = 50, message = "Time zone must not exceed 50 characters")
    private String timeZone;

    @Size(max = 10, message = "Language preference must not exceed 10 characters")
    private String languagePreference;

    @Size(max = 20, message = "Preferred contact method must not exceed 20 characters")
    private String preferredContactMethod;

    // Social media
    @Size(max = 500, message = "LinkedIn URL must not exceed 500 characters")
    private String linkedinUrl;

    @Size(max = 50, message = "Twitter handle must not exceed 50 characters")
    private String twitterHandle;

    // Emergency contact
    @Size(max = 200, message = "Emergency contact name must not exceed 200 characters")
    private String emergencyContactName;

    @Size(max = 50, message = "Emergency contact phone must not exceed 50 characters")
    private String emergencyContactPhone;

    @Size(max = 100, message = "Emergency contact relationship must not exceed 100 characters")
    private String emergencyContactRelationship;

    // Contact details
    private List<CreateEmailRequest> emails;
    private List<CreatePhoneRequest> phones;
    private List<CreateAddressRequest> addresses;

    // Notes
    private String notes;

    /**
     * Nested DTO for creating email.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateEmailRequest {
        @NotBlank(message = "Email address is required")
        @Email(message = "Invalid email address")
        private String emailAddress;
        private String emailType;
        private boolean isPrimary;
    }

    /**
     * Nested DTO for creating phone.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePhoneRequest {
        @NotBlank(message = "Phone number is required")
        private String phoneNumber;
        private String phoneType;
        private String countryCode;
        private String extension;
        private boolean isPrimary;
        private boolean canReceiveSms;
    }

    /**
     * Nested DTO for creating address.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAddressRequest {
        private String street1;
        private String street2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String addressType;
        private boolean isPrimary;
    }
}