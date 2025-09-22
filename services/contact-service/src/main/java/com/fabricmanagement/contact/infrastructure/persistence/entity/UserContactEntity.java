package com.fabricmanagement.contact.infrastructure.persistence.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Contact entity specifically for users.
 * Links contact information to a user in the user-service.
 * Extends ContactEntity which in turn extends BaseEntity for common functionality.
 */
@Entity
@Table(name = "user_contacts", indexes = {
    @Index(name = "idx_user_contact_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_user_contact_tenant_user", columnList = "tenant_id, user_id")
})
@DiscriminatorValue("USER")
@PrimaryKeyJoinColumn(name = "contact_id")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserContactEntity extends ContactEntity {

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @NotBlank(message = "User display name is required")
    @Size(max = 100, message = "User display name cannot exceed 100 characters")
    @Column(name = "user_display_name", nullable = false, length = 100)
    private String userDisplayName;

    // Personal contact information
    @Email(message = "Personal email must be valid")
    @Size(max = 100, message = "Personal email cannot exceed 100 characters")
    @Column(name = "personal_email", length = 100)
    private String personalEmail;

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{3,50}$", message = "Personal phone must be a valid phone number")
    @Size(max = 50, message = "Personal phone cannot exceed 50 characters")
    @Column(name = "personal_phone", length = 50)
    private String personalPhone;

    @Email(message = "Alternate email must be valid")
    @Size(max = 100, message = "Alternate email cannot exceed 100 characters")
    @Column(name = "alternate_email", length = 100)
    private String alternateEmail;

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{3,50}$", message = "Alternate phone must be a valid phone number")
    @Size(max = 50, message = "Alternate phone cannot exceed 50 characters")
    @Column(name = "alternate_phone", length = 50)
    private String alternatePhone;

    // Address information
    @Size(max = 500, message = "Home address cannot exceed 500 characters")
    @Column(name = "home_address", length = 500)
    private String homeAddress;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    @Column(name = "city", length = 100)
    private String city;

    @Size(max = 100, message = "State cannot exceed 100 characters")
    @Column(name = "state", length = 100)
    private String state;

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    @Column(name = "country", length = 100)
    private String country;

    // Emergency contact
    @Size(max = 100, message = "Emergency contact name cannot exceed 100 characters")
    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{3,50}$", message = "Emergency contact phone must be a valid phone number")
    @Size(max = 50, message = "Emergency contact phone cannot exceed 50 characters")
    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    @Size(max = 50, message = "Emergency contact relation cannot exceed 50 characters")
    @Column(name = "emergency_contact_relation", length = 50)
    private String emergencyContactRelation;

    // Communication preferences
    @Pattern(regexp = "^(EMAIL|PHONE|SMS)$", message = "Preferred contact method must be EMAIL, PHONE, or SMS")
    @Column(name = "preferred_contact_method", length = 10)
    private String preferredContactMethod;

    @Size(max = 50, message = "Time zone cannot exceed 50 characters")
    @Column(name = "time_zone", length = 50)
    private String timeZone;

    @Size(max = 10, message = "Language preference cannot exceed 10 characters")
    @Column(name = "language_preference", length = 10)
    private String languagePreference;

    // Privacy settings
    @Column(name = "public_profile")
    private Boolean publicProfile;

    @Column(name = "allow_direct_messages")
    private Boolean allowDirectMessages;

    @Column(name = "allow_notifications")
    private Boolean allowNotifications;

    /**
     * Creates a new UserContactEntity with the given user ID and tenant ID.
     */
    public static UserContactEntity createForUser(UUID userId, UUID tenantId, String userDisplayName) {
        UserContactEntity entity = new UserContactEntity();
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        entity.setUserDisplayName(userDisplayName);
        entity.setPreferredContactMethod("EMAIL");
        entity.setPublicProfile(false);
        entity.setAllowDirectMessages(true);
        entity.setAllowNotifications(true);
        return entity;
    }

    /**
     * Checks if this contact belongs to the specified user.
     */
    public boolean belongsToUser(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    /**
     * Gets the full address as a formatted string.
     */
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
}