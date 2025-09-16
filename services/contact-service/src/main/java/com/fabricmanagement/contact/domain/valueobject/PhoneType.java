package com.fabricmanagement.contact.domain.valueobject;

/**
 * Enum representing different types of phone numbers.
 */
public enum PhoneType {
    MOBILE("Mobile phone"),
    HOME("Home phone"),
    WORK("Work/Business phone"),
    FAX("Fax number"),
    EMERGENCY("Emergency contact"),
    MAIN("Main business line"),
    DIRECT("Direct line"),
    TOLL_FREE("Toll-free number");

    private final String description;

    PhoneType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
