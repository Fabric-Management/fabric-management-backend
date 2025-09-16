package com.fabricmanagement.contact.domain.valueobject;

/**
 * Enum representing different types of addresses.
 */
public enum AddressType {
    HOME("Home address"),
    WORK("Work/Business address"),
    BILLING("Billing address"),
    SHIPPING("Shipping address"),
    MAILING("Mailing address"),
    HEADQUARTERS("Company headquarters"),
    BRANCH("Branch office"),
    WAREHOUSE("Warehouse location"),
    TEMPORARY("Temporary address");

    private final String description;

    AddressType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
