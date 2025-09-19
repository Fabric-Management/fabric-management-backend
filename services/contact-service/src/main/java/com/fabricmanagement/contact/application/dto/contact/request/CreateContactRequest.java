package com.fabricmanagement.contact.application.dto.contact.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a contact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContactRequest {
    
    private UUID tenantId;
    private String contactType;
    private String firstName;
    private String lastName;
    private String displayName;
    private String notes;
    
    // User-specific fields
    private UUID userId;
    private String jobTitle;
    private String department;
    private String timeZone;
    private String languagePreference;
    private String preferredContactMethod;
    
    // Company-specific fields
    private UUID companyId;
    private String companyName;
    private String industry;
    private String companySize;
    private String website;
    private String taxId;
    private String registrationNumber;
    private String mainContactPerson;
    private String mainContactEmail;
    private String mainContactPhone;
    private String businessHours;
    private String paymentTerms;
    
    // Contact collections
    private java.util.List<String> emails;
    private java.util.List<String> phones;
    private java.util.List<String> addresses;
}