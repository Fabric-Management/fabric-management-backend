package com.fabricmanagement.contact.infrastructure.integration.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing user information from user-service.
 * Used for integration between contact-service and user-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private UUID id;
    private UUID tenantId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String status;
    private Boolean emailVerified;
    private Boolean twoFactorEnabled;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Gets the full name of the user.
     * @return the concatenated first and last name
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return String.format("%s %s", firstName, lastName).trim();
        }
        return "";
    }

    /**
     * Gets the display name (full name or username).
     * @return the display name
     */
    public String getDisplayName() {
        String fullName = getFullName();
        return !fullName.isEmpty() ? fullName : username;
    }

    /**
     * Checks if the user is active.
     * @return true if the user status is ACTIVE
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * Checks if the user has admin role.
     * @return true if the user has ADMIN or SUPER_ADMIN role
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }
}