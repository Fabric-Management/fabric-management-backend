package com.fabricmanagement.identity.domain.valueobject;

/**
 * Enum representing the type of contact.
 */
public enum ContactType {
    EMAIL("Email address"),
    PHONE("Phone number");

    private final String description;

    ContactType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}