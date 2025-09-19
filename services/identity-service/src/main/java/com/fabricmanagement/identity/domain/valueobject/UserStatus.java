package com.fabricmanagement.identity.domain.valueobject;

/**
 * Enum representing the status of a user account.
 */
public enum UserStatus {
    PENDING_ACTIVATION("Awaiting initial contact verification"),
    ACTIVE("Active and can authenticate"),
    SUSPENDED("Temporarily suspended by admin"),
    LOCKED("Locked due to failed login attempts");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}