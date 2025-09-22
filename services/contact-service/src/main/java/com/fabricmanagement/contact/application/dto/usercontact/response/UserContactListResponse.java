package com.fabricmanagement.contact.application.dto.usercontact.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for user contact list/summary view.
 * Contains essential user contact information for listing.
 */
@Builder
public record UserContactListResponse(

    UUID id,
    UUID userId,
    String userDisplayName,
    String status,

    // Primary contact information
    String personalEmail,
    String personalPhone,

    // Location summary
    String city,
    String state,
    String country,

    // Preferences summary
    String preferredContactMethod,
    Boolean publicProfile,

    // Metadata
    LocalDateTime createdAt,
    LocalDateTime updatedAt

) {}