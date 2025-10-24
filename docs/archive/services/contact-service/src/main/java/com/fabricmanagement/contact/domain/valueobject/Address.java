package com.fabricmanagement.contact.domain.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Address Value Object
 * 
 * Encapsulates address information and validation
 */
@Getter
@EqualsAndHashCode
@ToString
public class Address {
    
    private final String street;
    private final String city;
    private final String state;
    private final String postalCode;
    private final String country;
    private final String addressType; // HOME, WORK, BILLING, SHIPPING

    public Address(String street, String city, String state, 
                  String postalCode, String country, String addressType) {
        
        if (street == null || street.trim().isEmpty()) {
            throw new IllegalArgumentException("Street cannot be null or empty");
        }
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City cannot be null or empty");
        }
        if (country == null || country.trim().isEmpty()) {
            throw new IllegalArgumentException("Country cannot be null or empty");
        }
        
        this.street = street.trim();
        this.city = city.trim();
        this.state = state != null ? state.trim() : null;
        this.postalCode = postalCode != null ? postalCode.trim() : null;
        this.country = country.trim();
        this.addressType = addressType != null ? addressType.trim() : "HOME";
    }

    public static Address of(String street, String city, String state, 
                           String postalCode, String country, String addressType) {
        return new Address(street, city, state, postalCode, country, addressType);
    }

    /**
     * Gets formatted address string
     */
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(street);
        
        if (city != null) {
            sb.append(", ").append(city);
        }
        
        if (state != null) {
            sb.append(", ").append(state);
        }
        
        if (postalCode != null) {
            sb.append(" ").append(postalCode);
        }
        
        if (country != null) {
            sb.append(", ").append(country);
        }
        
        return sb.toString();
    }

    /**
     * Checks if address is complete
     */
    public boolean isComplete() {
        return street != null && !street.isEmpty() &&
               city != null && !city.isEmpty() &&
               country != null && !country.isEmpty();
    }
}
