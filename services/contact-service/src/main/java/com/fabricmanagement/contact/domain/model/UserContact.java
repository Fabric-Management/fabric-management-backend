package com.fabricmanagement.contact.domain.model;

import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * UserContact domain entity representing contact information for users.
 * Extends Contact base class for common contact functionality.
 * Focuses ONLY on user-specific contact information - NO user profile data.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserContact extends Contact {

    private UUID userId;
    private String userDisplayName;

    // Personal contact information
    private String personalEmail;
    private String personalPhone;
    private String alternateEmail;
    private String alternatePhone;

    // Address information
    private String homeAddress;
    private String city;
    private String state;
    private String postalCode;
    private String country;

    // Emergency contact
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelation;

    // Communication preferences
    private String preferredContactMethod;
    private String timeZone;
    private String languagePreference;

    // Privacy settings
    private boolean publicProfile;
    private boolean allowDirectMessages;
    private boolean allowNotifications;

    public UserContact(UUID tenantId, UUID userId, String userDisplayName,
                      String personalEmail, String personalPhone) {
        super(tenantId, "USER", "ACTIVE", null, null, userDisplayName, null);
        this.userId = userId;
        this.userDisplayName = userDisplayName;
        this.personalEmail = personalEmail;
        this.personalPhone = personalPhone;
        this.preferredContactMethod = "EMAIL";
        this.publicProfile = false;
        this.allowDirectMessages = true;
        this.allowNotifications = true;
    }

    // Domain behavior methods
    public void updatePreferredContactMethod(String method) {
        if ("EMAIL".equals(method) || "PHONE".equals(method) || "SMS".equals(method)) {
            this.preferredContactMethod = method;
        }
    }

    public void updatePrivacySettings(boolean publicProfile, boolean allowDirectMessages, boolean allowNotifications) {
        this.publicProfile = publicProfile;
        this.allowDirectMessages = allowDirectMessages;
        this.allowNotifications = allowNotifications;
    }

    public void updateEmergencyContact(String name, String phone, String relation) {
        this.emergencyContactName = name;
        this.emergencyContactPhone = phone;
        this.emergencyContactRelation = relation;
    }

    public boolean hasEmergencyContact() {
        return emergencyContactName != null && !emergencyContactName.trim().isEmpty() &&
               emergencyContactPhone != null && !emergencyContactPhone.trim().isEmpty();
    }

    public boolean hasHomeAddress() {
        return homeAddress != null && !homeAddress.trim().isEmpty();
    }

    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (homeAddress != null && !homeAddress.trim().isEmpty()) {
            address.append(homeAddress);
        }
        if (city != null && !city.trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(city);
        }
        if (state != null && !state.trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(state);
        }
        if (postalCode != null && !postalCode.trim().isEmpty()) {
            if (address.length() > 0) address.append(" ");
            address.append(postalCode);
        }
        if (country != null && !country.trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(country);
        }
        return address.toString();
    }

    public boolean canReceiveNotifications() {
        return isActive() && allowNotifications;
    }

    public boolean canReceiveDirectMessages() {
        return isActive() && allowDirectMessages;
    }

    @Override
    public String toString() {
        return "UserContact{" +
            "id=" + getId() +
            ", userId=" + userId +
            ", userDisplayName='" + userDisplayName + '\'' +
            ", personalEmail='" + personalEmail + '\'' +
            ", preferredContactMethod='" + preferredContactMethod + '\'' +
            ", status='" + getStatus() + '\'' +
            ", deleted=" + isDeleted() +
            '}';
    }
}