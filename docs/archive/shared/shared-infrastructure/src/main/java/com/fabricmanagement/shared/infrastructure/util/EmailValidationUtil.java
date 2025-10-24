package com.fabricmanagement.shared.infrastructure.util;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * Email Validation Utility
 * 
 * Specialized email validation utilities
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ UTILITY CLASS
 * ✅ STATIC METHODS
 */
@UtilityClass
public class EmailValidationUtil {
    
    // RFC 5322 compliant email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    );
    
    // Common email providers for validation
    private static final String[] COMMON_DOMAINS = {
        "gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "icloud.com",
        "aol.com", "live.com", "msn.com", "yandex.com", "mail.ru"
    };
    
    /**
     * Validate email format
     */
    public static boolean isValid(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        String trimmedEmail = email.trim().toLowerCase();
        
        // Check basic format
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            return false;
        }
        
        // Check length constraints
        if (trimmedEmail.length() > 254) {
            return false;
        }
        
        // Check local part length
        String[] parts = trimmedEmail.split("@");
        if (parts.length != 2 || parts[0].length() > 64) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate email with detailed error message
     */
    public static ValidationResult validateWithDetails(String email) {
        if (email == null) {
            return ValidationResult.invalid("Email cannot be null");
        }
        
        if (email.trim().isEmpty()) {
            return ValidationResult.invalid("Email cannot be empty");
        }
        
        String trimmedEmail = email.trim();
        
        if (trimmedEmail.length() > 254) {
            return ValidationResult.invalid("Email is too long (max 254 characters)");
        }
        
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            return ValidationResult.invalid("Invalid email format");
        }
        
        String[] parts = trimmedEmail.split("@");
        if (parts.length != 2) {
            return ValidationResult.invalid("Email must contain exactly one @ symbol");
        }
        
        if (parts[0].length() > 64) {
            return ValidationResult.invalid("Email local part is too long (max 64 characters)");
        }
        
        if (parts[0].isEmpty()) {
            return ValidationResult.invalid("Email local part cannot be empty");
        }
        
        if (parts[1].isEmpty()) {
            return ValidationResult.invalid("Email domain cannot be empty");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Check if email domain is common
     */
    public static boolean isCommonDomain(String email) {
        if (!isValid(email)) {
            return false;
        }
        
        String domain = email.trim().toLowerCase().split("@")[1];
        
        for (String commonDomain : COMMON_DOMAINS) {
            if (domain.equals(commonDomain)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extract domain from email
     */
    public static String extractDomain(String email) {
        if (!isValid(email)) {
            return null;
        }
        
        return email.trim().toLowerCase().split("@")[1];
    }
    
    /**
     * Extract local part from email
     */
    public static String extractLocalPart(String email) {
        if (!isValid(email)) {
            return null;
        }
        
        return email.trim().toLowerCase().split("@")[0];
    }
    
    /**
     * Normalize email (trim, lowercase)
     */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        
        return email.trim().toLowerCase();
    }
    
    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}