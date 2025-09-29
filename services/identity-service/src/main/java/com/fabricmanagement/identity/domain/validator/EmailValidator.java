package com.fabricmanagement.identity.domain.validator;

import java.util.regex.Pattern;

/**
 * Single Responsibility: Email validation only
 * Open/Closed: Can be extended without modification
 * Static utility class for email validation
 */
public class EmailValidator {
    
    private static final String EMAIL_PATTERN = 
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    
    private static final Pattern pattern = Pattern.compile(EMAIL_PATTERN);
    
    /**
     * Validates email format.
     *
     * @param email the email to validate
     * @return true if email is valid, false otherwise
     */
    public static boolean isValid(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return pattern.matcher(email).matches();
    }
    
    /**
     * Validates email format and throws exception if invalid.
     *
     * @param email the email to validate
     * @throws IllegalArgumentException if email is invalid
     */
    public static void validate(String email) {
        if (!isValid(email)) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
    }
}