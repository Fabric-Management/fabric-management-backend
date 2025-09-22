package com.fabricmanagement.contact.application.dto.contact.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Company contact response DTO as a record.
 * Focused only on contact information, not company business data.
 */
@Builder
public record CompanyContactResponse(
    UUID id,
    UUID tenantId,
    UUID companyId,
    String companyName,
    String contactType,
    String status,
    String displayName,
    String notes,

    // Contact-specific information
    String website,
    String mainContactPerson,
    String mainContactEmail,
    String mainContactPhone,
    String businessHours,

    // Related contact information
    List<EmailDto> emails,
    List<PhoneDto> phones,
    List<AddressDto> addresses,

    // Metadata
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    boolean isActive
) {
    // Validation and convenience methods
    public boolean hasMainContact() {
        return mainContactPerson != null && !mainContactPerson.trim().isEmpty();
    }

    public boolean hasWebsite() {
        return website != null && !website.trim().isEmpty();
    }

    public boolean hasBusinessHours() {
        return businessHours != null && !businessHours.trim().isEmpty();
    }
}