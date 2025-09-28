package com.fabricmanagement.company.infrastructure.integration.user;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for user information from user-service.
 */
@Data
@Builder
public class UserResponse {
    private UUID id;
    private UUID tenantId;

    // Basic user profile information
    private String firstName;
    private String lastName;
    private String displayName;
    private String jobTitle;
    private String department;

    // User preferences and settings
    private String timeZone;
    private String languagePreference;
    private String profileImageUrl;

    // User status
    private String status;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Derived fields
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return "";
    }
}