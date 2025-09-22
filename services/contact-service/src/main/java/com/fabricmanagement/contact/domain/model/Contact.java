package com.fabricmanagement.contact.domain.model;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Contact domain entity representing contact information in the system.
 * Extends BaseEntity for common functionality (id, auditing, soft delete).
 * Focused ONLY on contact-related data - NO user profile or company business data.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Contact extends BaseEntity {

    private UUID tenantId;
    private String contactType;
    private String status;

    // Basic identity fields (only for contacts that need names)
    private String firstName;
    private String lastName;
    private String displayName;
    private String notes;

    // Constructor
    public Contact(UUID tenantId, String contactType, String status,
                   String firstName, String lastName, String displayName, String notes) {
        super();
        this.tenantId = tenantId;
        this.contactType = contactType;
        this.status = status;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.notes = notes;
    }

    // Domain behavior methods
    @Override
    public void markAsDeleted() {
        super.markAsDeleted(); // Call parent's implementation for soft delete
        this.status = "INACTIVE"; // Update contact-specific status
    }

    public void activate() {
        this.status = "ACTIVE";
    }

    public void deactivate() {
        this.status = "INACTIVE";
    }

    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return displayName != null ? displayName : "";
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean hasName() {
        return (firstName != null && !firstName.trim().isEmpty()) ||
               (lastName != null && !lastName.trim().isEmpty()) ||
               (displayName != null && !displayName.trim().isEmpty());
    }

    @Override
    public String toString() {
        return "Contact{" +
            "id=" + getId() +
            ", tenantId=" + tenantId +
            ", contactType='" + contactType + '\'' +
            ", status='" + status + '\'' +
            ", displayName='" + displayName + '\'' +
            ", deleted=" + isDeleted() +
            '}';
    }
}