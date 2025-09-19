package com.fabricmanagement.identity.domain.valueobject;

/**
 * Enum representing user roles in the system.
 */
public enum UserRole {
    USER("Standard user", 1),
    MANAGER("Manager with extended permissions", 2),
    ADMIN("Administrator", 3),
    SUPER_ADMIN("Super administrator with full access", 4);

    private final String description;
    private final int level;

    UserRole(String description, int level) {
        this.description = description;
        this.level = level;
    }

    public String getDescription() {
        return description;
    }

    public int getLevel() {
        return level;
    }

    public boolean hasHigherOrEqualPrivilegeThan(UserRole other) {
        return this.level >= other.level;
    }
}