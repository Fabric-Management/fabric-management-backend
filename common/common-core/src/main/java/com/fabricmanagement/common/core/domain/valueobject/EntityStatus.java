package com.fabricmanagement.common.core.domain.valueobject;

/**
 * Enumeration for common entity status values.
 */
public enum EntityStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    PENDING("Pending"),
    SUSPENDED("Suspended"),
    DELETED("Deleted");

    private final String displayName;

    EntityStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isInactive() {
        return this == INACTIVE || this == SUSPENDED || this == DELETED;
    }
}
