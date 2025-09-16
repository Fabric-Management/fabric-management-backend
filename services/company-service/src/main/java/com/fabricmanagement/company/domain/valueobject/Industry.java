package com.fabricmanagement.company.domain.valueobject;

/**
 * Enum representing different industry sectors for companies.
 */
public enum Industry {
    TEXTILE("Textile and Apparel"),
    MANUFACTURING("Manufacturing"),
    TECHNOLOGY("Technology and Software"),
    HEALTHCARE("Healthcare and Pharmaceuticals"),
    FINANCE("Financial Services"),
    RETAIL("Retail and E-commerce"),
    CONSTRUCTION("Construction and Real Estate"),
    AUTOMOTIVE("Automotive"),
    FOOD_BEVERAGE("Food and Beverage"),
    ENERGY("Energy and Utilities"),
    EDUCATION("Education and Training"),
    TRANSPORTATION("Transportation and Logistics"),
    AGRICULTURE("Agriculture and Farming"),
    ENTERTAINMENT("Entertainment and Media"),
    CONSULTING("Consulting and Professional Services"),
    OTHER("Other");

    private final String description;

    Industry(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
