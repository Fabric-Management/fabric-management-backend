package com.fabricmanagement.user.domain.valueobject;

/**
 * Enum representing the status of a user in the system.
 */
public enum UserStatus {
    ACTIVE("Active user"),
    INACTIVE("Inactive user"),
    PENDING("Pending activation"),
    SUSPENDED("Suspended user"),
    DELETED("Deleted user");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
