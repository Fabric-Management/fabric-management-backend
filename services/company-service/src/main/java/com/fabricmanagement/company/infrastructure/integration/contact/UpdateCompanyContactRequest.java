package com.fabricmanagement.company.infrastructure.integration.contact;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for updating company contact information in contact-service.
 */
@Data
@Builder
public class UpdateCompanyContactRequest {
    // Contact person information
    private String contactPersonName;
    private String contactPersonTitle;

    // Contact details
    private List<UpdateEmailRequest> emails;
    private List<UpdatePhoneRequest> phones;
    private List<UpdateAddressRequest> addresses;

    // Website and social media
    private String website;
    private String linkedinUrl;

    // Notes
    private String notes;

    @Data
    @Builder
    public static class UpdateEmailRequest {
        private String email;
        private String type;
        private boolean isPrimary;
    }

    @Data
    @Builder
    public static class UpdatePhoneRequest {
        private String phoneNumber;
        private String type;
        private String countryCode;
        private boolean isPrimary;
    }

    @Data
    @Builder
    public static class UpdateAddressRequest {
        private String type;
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private boolean isPrimary;
    }
}