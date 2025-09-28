package com.fabricmanagement.company.domain.valueobject;

/**
 * Enum representing supported currencies for financial data.
 */
public enum Currency {
    USD("US Dollar"),
    EUR("Euro"),
    GBP("British Pound"),
    TRY("Turkish Lira"),
    CNY("Chinese Yuan"),
    JPY("Japanese Yen"),
    KRW("South Korean Won"),
    INR("Indian Rupee");

    private final String description;

    Currency(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}