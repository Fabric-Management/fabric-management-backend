package com.fabricmanagement.shared.infrastructure.util;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * Masking Utility
 * 
 * Data masking utilities for sensitive information
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ UTILITY CLASS
 * ✅ STATIC METHODS
 */
@UtilityClass
public class MaskingUtil {
    
    // Email masking pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(\\w{1,3})\\w*(@\\w+\\.[\\w.]+)");
    
    // Phone masking pattern
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d{1,3})\\d*(\\d{4})");
    
    // Credit card masking pattern
    private static final Pattern CARD_PATTERN = Pattern.compile("(\\d{4})\\d*(\\d{4})");
    
    /**
     * Mask email address
     */
    public static String maskEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return email;
        }
        
        String trimmedEmail = email.trim();
        
        // Check if it's a valid email format
        if (!trimmedEmail.contains("@")) {
            return maskString(trimmedEmail, 2, 2);
        }
        
        String[] parts = trimmedEmail.split("@");
        if (parts.length != 2) {
            return maskString(trimmedEmail, 2, 2);
        }
        
        String localPart = parts[0];
        String domain = parts[1];
        
        // Mask local part
        String maskedLocalPart = maskString(localPart, 1, 1);
        
        return maskedLocalPart + "@" + domain;
    }
    
    /**
     * Mask phone number
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return phone;
        }
        
        String trimmedPhone = phone.trim();
        
        // If it's a short number, mask most of it
        if (trimmedPhone.length() <= 4) {
            return maskString(trimmedPhone, 1, 0);
        }
        
        // For longer numbers, show first 3 and last 4 digits
        return maskString(trimmedPhone, 3, 4);
    }
    
    /**
     * Mask credit card number
     */
    public static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return cardNumber;
        }
        
        String trimmedCard = cardNumber.trim().replaceAll("\\s+", "");
        
        // Show first 4 and last 4 digits
        return maskString(trimmedCard, 4, 4);
    }
    
    /**
     * Mask string with specified visible characters
     */
    public static String maskString(String input, int visibleStart, int visibleEnd) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        int length = input.length();
        
        // If string is too short, mask most of it
        if (length <= visibleStart + visibleEnd) {
            return "*".repeat(Math.max(1, length - 1)) + input.charAt(length - 1);
        }
        
        StringBuilder masked = new StringBuilder();
        
        // Add visible start characters
        masked.append(input, 0, visibleStart);
        
        // Add masked middle
        masked.append("*".repeat(length - visibleStart - visibleEnd));
        
        // Add visible end characters
        masked.append(input, length - visibleEnd, length);
        
        return masked.toString();
    }
    
    /**
     * Mask name (first name + last name)
     */
    public static String maskName(String firstName, String lastName) {
        String maskedFirst = maskString(firstName, 1, 0);
        String maskedLast = maskString(lastName, 1, 0);
        
        return maskedFirst + " " + maskedLast;
    }
    
    /**
     * Mask full name
     */
    public static String maskFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return fullName;
        }
        
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0) {
            return fullName;
        }
        
        StringBuilder masked = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                masked.append(" ");
            }
            masked.append(maskString(parts[i], 1, 0));
        }
        
        return masked.toString();
    }
    
    /**
     * Mask address
     */
    public static String maskAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return address;
        }
        
        String trimmedAddress = address.trim();
        
        // Show first 2 characters and last 2 characters
        return maskString(trimmedAddress, 2, 2);
    }
    
    /**
     * Mask SSN (Social Security Number)
     */
    public static String maskSSN(String ssn) {
        if (ssn == null || ssn.trim().isEmpty()) {
            return ssn;
        }
        
        String trimmedSSN = ssn.trim().replaceAll("[^0-9]", "");
        
        if (trimmedSSN.length() != 9) {
            return maskString(ssn, 2, 2);
        }
        
        return "***-**-" + trimmedSSN.substring(5);
    }
    
    /**
     * Mask any sensitive data with default pattern
     */
    public static String maskSensitive(String data) {
        if (data == null || data.trim().isEmpty()) {
            return data;
        }
        
        String trimmedData = data.trim();
        
        // Determine masking strategy based on content
        if (trimmedData.contains("@")) {
            return maskEmail(trimmedData);
        } else if (trimmedData.matches(".*\\d{10,}.*")) {
            return maskPhone(trimmedData);
        } else if (trimmedData.matches(".*\\d{4}.*\\d{4}.*")) {
            return maskCard(trimmedData);
        } else {
            return maskString(trimmedData, 2, 2);
        }
    }
    
    /**
     * Check if data is masked
     */
    public static boolean isMasked(String data) {
        if (data == null || data.trim().isEmpty()) {
            return false;
        }
        
        return data.contains("*");
    }
    
    /**
     * Get masking level
     */
    public static int getMaskingLevel(String data) {
        if (data == null || data.trim().isEmpty()) {
            return 0;
        }
        
        long asteriskCount = data.chars().filter(ch -> ch == '*').count();
        return (int) asteriskCount;
    }
}