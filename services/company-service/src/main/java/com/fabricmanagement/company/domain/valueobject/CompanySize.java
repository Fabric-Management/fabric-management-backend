package com.fabricmanagement.company.domain.valueobject;

/**
 * Enum representing the size of a company based on employee count.
 */
public enum CompanySize {
    STARTUP("Startup (1-10 employees)"),
    SMALL("Small (11-50 employees)"),
    MEDIUM("Medium (51-200 employees)"),
    LARGE("Large (201-1000 employees)"),
    ENTERPRISE("Enterprise (1000+ employees)");

    private final String description;

    CompanySize(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}