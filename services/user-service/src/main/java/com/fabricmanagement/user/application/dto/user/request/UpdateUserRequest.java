package com.fabricmanagement.user.application.dto.user.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

/**
 * Request DTO for updating an existing user as a record.
 * All fields are optional for partial updates.
 */
@Builder
public record UpdateUserRequest(
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    String firstName,

    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    String lastName,

    @Size(max = 150, message = "Display name cannot exceed 150 characters")
    String displayName,

    @Size(max = 100, message = "Job title cannot exceed 100 characters")
    String jobTitle,

    @Size(max = 100, message = "Department cannot exceed 100 characters")
    String department,

    @Pattern(regexp = "^[A-Za-z]+/[A-Za-z_]+$",
             message = "Time zone must be in format 'Continent/City' (e.g., 'America/New_York')")
    String timeZone,

    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$",
             message = "Language preference must be in format 'en' or 'en-US'")
    String languagePreference,

    @Pattern(regexp = "^https?://.*\\.(jpg|jpeg|png|gif)$",
             message = "Profile image URL must be a valid image URL")
    String profileImageUrl
) {
    public boolean hasProfileUpdate() {
        return firstName != null || lastName != null || displayName != null ||
               jobTitle != null || department != null;
    }

    public boolean hasPreferenceUpdate() {
        return timeZone != null || languagePreference != null || profileImageUrl != null;
    }
}

