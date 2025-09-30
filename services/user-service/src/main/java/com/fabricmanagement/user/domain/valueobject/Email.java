package com.fabricmanagement.user.domain.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Pattern;

/**
 * Email Value Object
 * 
 * Encapsulates email validation and business rules
 */
@Getter
@EqualsAndHashCode
@ToString
public class Email {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final String ERROR_MESSAGE = "Email must be a valid email address";
    
    private final String value;

    public Email(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        
        String trimmedValue = value.trim().toLowerCase();
        
        if (!EMAIL_PATTERN.matcher(trimmedValue).matches()) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
        
        this.value = trimmedValue;
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
