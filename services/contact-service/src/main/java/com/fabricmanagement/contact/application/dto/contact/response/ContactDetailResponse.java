package com.fabricmanagement.contact.application.dto.contact.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detailed contact response DTO.
 * Used for detailed views with all related information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDetailResponse {

    private UUID id;
    private UUID tenantId;
    private Object contactType;  // Can be enum or string
    private Object status;       // Can be enum or string
    private String notes;

    // Basic identity fields
    private String firstName;
    private String lastName;
    private String displayName;

    // User-specific fields
    private UUID userId;
    private String jobTitle;
    private String department;
    private String linkedinUrl;
    private String twitterHandle;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelationship;
    private String preferredContactMethod;
    private String timeZone;
    private String languagePreference;

    // Company-specific fields
    private UUID companyId;
    private String companyName;
    private String industry;
    private String position;
    private String businessUnit;
    private String companyWebsite;
    private String taxId;
    private String registrationNumber;

    // Related data collections
    private List<Object> emails;     // List of email DTOs
    private List<Object> phones;     // List of phone DTOs
    private List<Object> addresses;  // List of address DTOs

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
    private Boolean deleted;
}