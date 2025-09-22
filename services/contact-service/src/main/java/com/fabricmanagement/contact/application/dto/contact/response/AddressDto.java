package com.fabricmanagement.contact.application.dto.contact.response;

import lombok.Builder;

import java.util.UUID;

/**
 * Address DTO as a record.
 */
@Builder
public record AddressDto(
    UUID id,
    String street1,
    String street2,
    String city,
    String state,
    String zipCode,
    String country,
    String type,
    boolean isPrimary,
    String description
) {
    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (street1 != null) address.append(street1);
        if (street2 != null) address.append(", ").append(street2);
        if (city != null) address.append(", ").append(city);
        if (state != null) address.append(", ").append(state);
        if (zipCode != null) address.append(" ").append(zipCode);
        if (country != null) address.append(", ").append(country);
        return address.toString().replaceFirst("^, ", "").trim();
    }

    public String getDisplayText() {
        return isPrimary ? getFullAddress() + " (Primary)" : getFullAddress();
    }
}