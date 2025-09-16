package com.fabricmanagement.contact.domain.valueobject;

/**
 * Enum representing different types of email addresses.
 */
public enum EmailType {
    PERSONAL("Personal email"),
    WORK("Work/Business email"),
    PRIMARY("Primary contact email"),
    SECONDARY("Secondary contact email"),
    BILLING("Billing and invoicing"),
    SUPPORT("Customer support"),
    MARKETING("Marketing communications"),
    TECHNICAL("Technical correspondence");

    private final String description;

    EmailType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
