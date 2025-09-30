package com.fabricmanagement.user.domain.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Pattern;

/**
 * Username Value Object
 * 
 * Encapsulates username validation and business rules
 */
@Getter
@EqualsAndHashCode
@ToString
public class Username {
    
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");
    private static final String ERROR_MESSAGE = "Username must be 3-50 characters long and contain only letters, numbers, dots, underscores, and hyphens";
    
    private final String value;

    public Username(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        
        String trimmedValue = value.trim().toLowerCase();
        
        if (!USERNAME_PATTERN.matcher(trimmedValue).matches()) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
        
        this.value = trimmedValue;
    }

    public static Username of(String value) {
        return new Username(value);
    }
}
