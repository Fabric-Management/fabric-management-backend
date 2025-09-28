package com.fabricmanagement.company.infrastructure.integration.user;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Basic user response DTO for minimal user information from user-service.
 * Used for company-user relationships where full user data is not needed.
 */
@Data
@Builder
public class UserBasicResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String displayName;
    private String jobTitle;
    private String status;

    // Derived field
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