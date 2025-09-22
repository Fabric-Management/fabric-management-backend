package com.fabricmanagement.user.application.dto.user.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

/**
 * Request DTO for creating a new user as a record.
 * Focused only on user profile information.
 */
@Builder
public record CreateUserRequest(
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
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
    public boolean hasDisplayName() {
        return displayName != null && !displayName.trim().isEmpty();
    }

    public boolean hasJobDetails() {
        return (jobTitle != null && !jobTitle.trim().isEmpty()) ||
               (department != null && !department.trim().isEmpty());
    }

    public boolean hasPreferences() {
        return timeZone != null || languagePreference != null;
    }
}

