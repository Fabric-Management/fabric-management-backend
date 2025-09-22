package com.fabricmanagement.contact.application.dto.usercontact.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for user contact details.
 * Contains complete user contact information.
 */
@Builder
public record UserContactResponse(

    UUID id,
    UUID userId,
    UUID tenantId,
    String userDisplayName,
    String status,

    // Personal contact information
    String personalEmail,
    String personalPhone,
    String alternateEmail,
    String alternatePhone,

    // Address information
    String homeAddress,
    String city,
    String state,
    String postalCode,
    String country,
    String fullAddress,

    // Emergency contact
    String emergencyContactName,
    String emergencyContactPhone,
    String emergencyContactRelation,

    // Communication preferences
    String preferredContactMethod,
    String timeZone,
    String languagePreference,

    // Privacy settings
    Boolean publicProfile,
    Boolean allowDirectMessages,
    Boolean allowNotifications,

    // Metadata
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String createdBy,
    String updatedBy

) {}