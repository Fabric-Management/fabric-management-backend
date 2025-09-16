package com.fabricmanagement.company.domain.valueobject;

/**
 * Enum representing the type of company in the system.
 */
public enum CompanyType {
    MANUFACTURER("Manufacturing company"),
    SUPPLIER("Supplier company"),
    RETAILER("Retail company"),
    DISTRIBUTOR("Distribution company"),
    WHOLESALER("Wholesale company"),
    SERVICE_PROVIDER("Service provider"),
    STARTUP("Startup company"),
    CORPORATION("Large corporation"),
    SME("Small/Medium enterprise");

    private final String description;

    CompanyType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
