package com.fabricmanagement.contact.application.dto.contact.response;

import com.fabricmanagement.contact.infrastructure.persistence.entity.UserContactEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Basic contact response DTO.
 * Used for list views and summary information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponse {

    private UUID id;
    private UUID tenantId;
    private String contactType;
    private String status;

    // Basic identity fields
    private String firstName;
    private String lastName;
    private String displayName;

    // Primary contact info
    private String primaryEmail;
    private String primaryPhone;
    private String primaryAddress;

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
    private String website;
    private String position;
    private String businessUnit;
    private String mainContactPerson;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;

    /**
     * Sets contact type as Object to support both enum and string.
     */
    public void setContactType(Object type) {
        if (type == null) {
            this.contactType = null;
        } else if (type instanceof String) {
            this.contactType = (String) type;
        } else {
            this.contactType = type.toString();
        }
    }

    /**
     * Sets status as Object to support both enum and string.
     */
    public void setStatus(Object status) {
        if (status == null) {
            this.status = null;
        } else if (status instanceof String) {
            this.status = (String) status;
        } else {
            this.status = status.toString();
        }
    }

    /**
     * Factory method to create ContactResponse from UserContactEntity
     */
    public static ContactResponse fromUserContact(UserContactEntity entity) {
        return ContactResponse.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .contactType("USER")
            .status(entity.getStatus() != null ? entity.getStatus().name() : null)
            .firstName(entity.getFirstName())
            .lastName(entity.getLastName())
            .displayName(entity.getDisplayName())
            .primaryEmail(entity.getPrimaryEmail() != null ? entity.getPrimaryEmail().getEmail() : null)
            .primaryPhone(entity.getPrimaryPhone() != null ? entity.getPrimaryPhone().getPhoneNumber() : null)
            .primaryAddress(entity.getPrimaryAddress() != null ? entity.getPrimaryAddress().getFullAddress() : null)
            .userId(entity.getUserId())
            .jobTitle(entity.getJobTitle())
            .department(entity.getDepartment())
            .timeZone(entity.getTimeZone())
            .languagePreference(entity.getLanguagePreference())
            .preferredContactMethod(entity.getPreferredContactMethod())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .isActive(entity.getStatus() != null && "ACTIVE".equals(entity.getStatus().name()))
            .build();
    }

    /**
     * Factory method to create a simple ContactResponse
     */
    public static ContactResponse simple(UUID id, String firstName, String lastName, String email, String phone) {
        return ContactResponse.builder()
            .id(id)
            .firstName(firstName)
            .lastName(lastName)
            .displayName(firstName + " " + lastName)
            .primaryEmail(email)
            .primaryPhone(phone)
            .isActive(true)
            .build();
    }
}