package com.fabricmanagement.contact.application.dto.contact.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact list response DTO as a record.
 * Used for listing contacts with minimal information.
 */
@Builder
public record ContactListResponse(
    UUID id,
    UUID tenantId,
    UUID companyId,
    String companyName,
    String contactType,
    String status,
    String displayName,
    String website,
    String mainContactPerson,
    String primaryEmail,
    String primaryPhone,
    String primaryAddress,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    boolean isActive
) {
    public boolean hasContactInfo() {
        return (primaryEmail != null && !primaryEmail.trim().isEmpty()) ||
               (primaryPhone != null && !primaryPhone.trim().isEmpty());
    }
}