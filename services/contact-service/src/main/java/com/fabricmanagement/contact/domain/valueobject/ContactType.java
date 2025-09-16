package com.fabricmanagement.contact.domain.valueobject;

/**
 * Enum representing the type of contact in the system.
 */
public enum ContactType {
    CUSTOMER("Customer contact"),
    SUPPLIER("Supplier contact"),
    PARTNER("Business partner"),
    EMPLOYEE("Employee contact"),
    CONTRACTOR("Contractor contact"),
    PROSPECT("Potential customer");

    private final String description;

    ContactType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
