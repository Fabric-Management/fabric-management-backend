package com.fabricmanagement.shared.infrastructure.util;

import lombok.experimental.UtilityClass;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Centralized Validation Utilities
 * 
 * Purpose: Reusable validation logic across all services
 * Principle: ZERO HARDCODED - Uses ValidationConstants
 * 
 * @since 1.0.0
 */
@UtilityClass
public class ValidationUtil {
    
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$" // E.164 format
    );
    
    /**
     * Validate UUID string format
     * 
     * @param uuid String to validate
     * @return true if valid UUID format
     */
    public static boolean isValidUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(uuid).matches();
    }
    
    /**
     * Validate and parse UUID string
     * 
     * @param uuid String to parse
     * @return UUID if valid, null if invalid
     */
    public static UUID parseUUID(String uuid) {
        if (!isValidUUID(uuid)) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Validate email format (RFC 5322 simplified)
     * 
     * @param email Email to validate
     * @return true if valid email format
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Validate phone number (E.164 format)
     * 
     * @param phone Phone number to validate
     * @return true if valid phone format
     */
    public static boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }
    
    /**
     * Check if value is within range (inclusive)
     * 
     * @param value Value to check
     * @param min Minimum (inclusive)
     * @param max Maximum (inclusive)
     * @return true if within range
     */
    public static boolean isWithinRange(int value, int min, int max) {
        return value >= min && value <= max;
    }
    
    /**
     * Check if string length is within range
     * 
     * @param value String to check
     * @param minLength Minimum length (inclusive)
     * @param maxLength Maximum length (inclusive)
     * @return true if length within range
     */
    public static boolean isLengthValid(String value, int minLength, int maxLength) {
        if (value == null) {
            return false;
        }
        return isWithinRange(value.length(), minLength, maxLength);
    }
    
    /**
     * Validate string is not blank (not null, not empty, not whitespace)
     * 
     * @param value String to check
     * @return true if not blank
     */
    public static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
    
    /**
     * Validate string matches alphanumeric pattern (letters, numbers, hyphens)
     * Used for codes, slugs, etc.
     * 
     * @param value String to validate
     * @return true if alphanumeric with hyphens
     */
    public static boolean isAlphanumericWithHyphens(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.matches("^[A-Z0-9-]+$");
    }
    
    /**
     * Validate positive number (greater than zero)
     * 
     * @param value Number to validate
     * @return true if positive
     */
    public static boolean isPositive(Number value) {
        if (value == null) {
            return false;
        }
        return value.doubleValue() > 0;
    }
    
    /**
     * Validate non-negative number (greater than or equal to zero)
     * 
     * @param value Number to validate
     * @return true if non-negative
     */
    public static boolean isNonNegative(Number value) {
        if (value == null) {
            return false;
        }
        return value.doubleValue() >= 0;
    }
}

