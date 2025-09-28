package com.fabricmanagement.company.domain.valueobject;

/**
 * Enum representing the operational status of a company.
 */
public enum CompanyStatus {
    ACTIVE("Active and operational"),
    INACTIVE("Inactive"),
    PENDING("Pending verification"),
    SUSPENDED("Suspended"),
    BLACKLISTED("Blacklisted");

    private final String description;

    CompanyStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}