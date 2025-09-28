package com.fabricmanagement.company.infrastructure.integration.contact;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating company contact information in contact-service.
 */
@Data
@Builder
public class CreateCompanyContactRequest {
    private UUID companyId;
    private String contactType;

    // Contact person information
    private String contactPersonName;
    private String contactPersonTitle;

    // Contact details
    private List<CreateEmailRequest> emails;
    private List<CreatePhoneRequest> phones;
    private List<CreateAddressRequest> addresses;

    // Website and social media
    private String website;
    private String linkedinUrl;

    // Notes
    private String notes;

    @Data
    @Builder
    public static class CreateEmailRequest {
        private String email;
        private String type;
        private boolean isPrimary;
    }

    @Data
    @Builder
    public static class CreatePhoneRequest {
        private String phoneNumber;
        private String type;
        private String countryCode;
        private boolean isPrimary;
    }

    @Data
    @Builder
    public static class CreateAddressRequest {
        private String type;
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private boolean isPrimary;
    }
}