package com.fabricmanagement.company.infrastructure.integration.contact;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for company contact information from contact-service.
 */
@Data
@Builder
public class CompanyContactResponse {
    private UUID companyId;
    private UUID contactId;
    private String contactType;
    private String status;

    // Contact person information
    private String contactPersonName;
    private String contactPersonTitle;

    // Contact details
    private List<EmailDto> emails;
    private List<PhoneDto> phones;
    private List<AddressDto> addresses;

    // Website and social media
    private String website;
    private String linkedinUrl;

    // Notes and additional info
    private String notes;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class EmailDto {
        private UUID id;
        private String email;
        private String type;
        private boolean isPrimary;
    }

    @Data
    @Builder
    public static class PhoneDto {
        private UUID id;
        private String phoneNumber;
        private String type;
        private String countryCode;
        private boolean isPrimary;
    }

    @Data
    @Builder
    public static class AddressDto {
        private UUID id;
        private String type;
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private boolean isPrimary;
    }
}