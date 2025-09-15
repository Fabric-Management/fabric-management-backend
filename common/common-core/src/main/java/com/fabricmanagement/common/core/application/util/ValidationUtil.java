package com.fabricmanagement.common.core.application.util;

import java.util.regex.Pattern;

/**
 * Shared validation utilities for all microservices
 */
public class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"
    );

    private ValidationUtil() {
        // Utility class
    }

    /**
     * Validates if a string is not null and not empty
     */
    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Validates email format
     */
    public static boolean isValidEmail(String email) {
        return isNotEmpty(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validates phone number format
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        return isNotEmpty(phoneNumber) && PHONE_PATTERN.matcher(phoneNumber).matches();
    }

    /**
     * Validates if a string meets minimum length requirement
     */
    public static boolean hasMinLength(String value, int minLength) {
        return isNotEmpty(value) && value.trim().length() >= minLength;
    }

    /**
     * Validates if a string meets maximum length requirement
     */
    public static boolean hasMaxLength(String value, int maxLength) {
        return value == null || value.trim().length() <= maxLength;
    }

    /**
     * Validates if a string is within specified length range
     */
    public static boolean isLengthValid(String value, int minLength, int maxLength) {
        return hasMinLength(value, minLength) && hasMaxLength(value, maxLength);
    }
}
