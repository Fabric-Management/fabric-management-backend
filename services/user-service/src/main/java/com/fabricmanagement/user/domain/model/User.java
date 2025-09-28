package com.fabricmanagement.user.domain.model;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * User domain entity representing user profiles in the system.
 * Extends BaseEntity for common functionality (id, auditing, soft delete).
 * Focused ONLY on user profile data - NO authentication/authorization or contact data.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    private UUID tenantId;
    private String username; // Username from Identity Service

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

    // User status (different from BaseEntity's deleted flag)
    private UserStatus status;

    // Business domain behavior
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return "";
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public boolean isActive() {
        return UserStatus.ACTIVE.equals(status);
    }

    public boolean hasJobTitle() {
        return jobTitle != null && !jobTitle.trim().isEmpty();
    }

    public boolean hasDepartment() {
        return department != null && !department.trim().isEmpty();
    }

    public void updateProfile(String firstName, String lastName, String displayName,
                             String jobTitle, String department) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.jobTitle = jobTitle;
        this.department = department;
    }

    public void updatePreferences(String timeZone, String languagePreference) {
        this.timeZone = timeZone;
        this.languagePreference = languagePreference;
    }

    @Override
    public String toString() {
        return "User{" +
            "id=" + getId() +
            ", tenantId=" + tenantId +
            ", firstName='" + firstName + '\'' +
            ", lastName='" + lastName + '\'' +
            ", displayName='" + displayName + '\'' +
            ", status=" + status +
            ", deleted=" + isDeleted() +
            '}';
    }
}