package com.fabricmanagement.contact.domain.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Pattern;

/**
 * Phone Number Value Object
 * 
 * Encapsulates phone number validation and business rules
 */
@Getter
@EqualsAndHashCode
@ToString
public class PhoneNumber {
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$" // E.164 format
    );
    private static final String ERROR_MESSAGE = "Phone number must be in valid E.164 format";
    
    private final String value;
    private final String countryCode;
    private final String nationalNumber;

    public PhoneNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }
        
        String cleanedValue = value.trim().replaceAll("[\\s\\-\\(\\)]", "");
        
        if (!PHONE_PATTERN.matcher(cleanedValue).matches()) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
        
        this.value = cleanedValue;
        
        // Extract country code and national number
        if (cleanedValue.startsWith("+")) {
            this.countryCode = cleanedValue.substring(1, 3); // Assuming 2-digit country code
            this.nationalNumber = cleanedValue.substring(3);
        } else {
            this.countryCode = "1"; // Default to US
            this.nationalNumber = cleanedValue;
        }
    }

    public static PhoneNumber of(String value) {
        return new PhoneNumber(value);
    }

    /**
     * Formats phone number for display
     */
    public String getFormattedNumber() {
        if (nationalNumber.length() == 10) {
            return String.format("(%s) %s-%s", 
                nationalNumber.substring(0, 3),
                nationalNumber.substring(3, 6),
                nationalNumber.substring(6));
        }
        return value;
    }
}
