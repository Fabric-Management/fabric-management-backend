package com.fabricmanagement.contact.application.dto.contact.response;

import lombok.Builder;

import java.util.UUID;

/**
 * Phone DTO as a record.
 */
@Builder
public record PhoneDto(
    UUID id,
    String countryCode,
    String areaCode,
    String number,
    String extension,
    String type,
    boolean isPrimary,
    String description
) {
    public String getFullNumber() {
        StringBuilder phone = new StringBuilder();
        if (countryCode != null) phone.append("+").append(countryCode).append(" ");
        if (areaCode != null) phone.append("(").append(areaCode).append(") ");
        if (number != null) phone.append(number);
        if (extension != null) phone.append(" ext. ").append(extension);
        return phone.toString().trim();
    }

    public String getDisplayText() {
        return isPrimary ? getFullNumber() + " (Primary)" : getFullNumber();
    }
}