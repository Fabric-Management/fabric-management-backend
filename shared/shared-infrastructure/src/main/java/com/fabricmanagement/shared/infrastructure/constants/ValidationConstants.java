package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Validation Constants
 * 
 * Centralized validation constants to avoid magic numbers/strings
 */
public final class ValidationConstants {
    
    private ValidationConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // String Length Constants
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_ADDRESS_LENGTH = 255;
    public static final int MAX_EMAIL_LENGTH = 255;
    public static final int MAX_PHONE_LENGTH = 20;
    public static final int MAX_URL_LENGTH = 500;
    
    // Regex Patterns
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String PHONE_PATTERN = "^\\+?[1-9]\\d{1,14}$";
    public static final String URL_PATTERN = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$";
    
    // Numeric Constants
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;
    
    // Business Constants
    public static final int MIN_EMPLOYEE_COUNT = 1;
    public static final int MAX_EMPLOYEE_COUNT = 100000;
    
    // Validation Messages
    public static final String MSG_REQUIRED = "This field is required";
    public static final String MSG_INVALID_EMAIL = "Invalid email format";
    public static final String MSG_INVALID_PHONE = "Invalid phone number format";
    public static final String MSG_INVALID_URL = "Invalid URL format";
    public static final String MSG_TOO_LONG = "Value exceeds maximum length";
    public static final String MSG_TOO_SHORT = "Value is below minimum length";
}

