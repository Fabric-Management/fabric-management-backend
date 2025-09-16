package com.fabricmanagement.contact.domain.valueobject;

/**
 * Enum representing the status of a contact in the system.
 */
public enum ContactStatus {
    ACTIVE("Active contact"),
    INACTIVE("Inactive contact"),
    PENDING("Pending verification"),
    BLOCKED("Blocked contact"),
    ARCHIVED("Archived contact");

    private final String description;

    ContactStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
