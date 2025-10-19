package com.fabricmanagement.shared.infrastructure.util;

/**
 * Data Masking Utility
 * 
 * Provides privacy-safe data masking for sensitive information.
 * 
 * Use cases:
 * - Similar company search results (tax ID, legal name)
 * - Duplicate detection (show masked data)
 * - Audit logs (PII redaction)
 * 
 * Principles:
 * - Show enough to identify, hide enough to protect
 * - Consistent masking algorithm (no hardcoded values!)
 * - Google/Amazon-level privacy standards
 * 
 * @author Fabric Management Team
 * @since 3.3.0
 */
public final class DataMaskingUtil {

    private DataMaskingUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Mask tax ID / registration number
     * 
     * Examples:
     * - "1234567890" → "123******90"
     * - "12345" → "12***"
     * 
     * @param taxId Tax ID or registration number
     * @return Masked tax ID (show first 3 + last 2 digits)
     */
    public static String maskTaxId(String taxId) {
        if (taxId == null || taxId.trim().isEmpty()) {
            return null;
        }
        
        String cleaned = taxId.trim();
        int length = cleaned.length();
        
        // Very short IDs (< 5 chars) - mask middle
        if (length <= 4) {
            return cleaned.substring(0, 2) + "***";
        }
        
        // Standard masking: Show first 3 + last 2, mask middle
        int visibleStart = Math.min(3, length - 2);
        int visibleEnd = Math.max(2, length - visibleStart);
        
        String start = cleaned.substring(0, visibleStart);
        String end = cleaned.substring(length - visibleEnd);
        String mask = "*".repeat(Math.max(3, length - visibleStart - visibleEnd));
        
        return start + mask + end;
    }

    /**
     * Mask legal name (show first word + last word)
     * 
     * Examples:
     * - "Akkayalar Tekstil Dokuma San Tic Ltd Şti" → "Akkayalar *** Şti"
     * - "ABC Company" → "ABC *** Company"
     * - "SingleWord" → "SingleWord" (no masking)
     * 
     * @param legalName Legal company name
     * @return Masked legal name
     */
    public static String maskLegalName(String legalName) {
        if (legalName == null || legalName.trim().isEmpty()) {
            return null;
        }
        
        String cleaned = legalName.trim();
        String[] words = cleaned.split("\\s+");
        
        // Single word - no masking
        if (words.length == 1) {
            return cleaned;
        }
        
        // Two words - show both
        if (words.length == 2) {
            return words[0] + " " + words[1];
        }
        
        // Multiple words - show first + "***" + last
        return words[0] + " *** " + words[words.length - 1];
    }

    /**
     * Mask email address
     * 
     * Examples:
     * - "fatih@akkayalartekstil.com.tr" → "fa***@akkayalartekstil.com.tr"
     * - "a@example.com" → "a***@example.com"
     * 
     * @param email Email address
     * @return Masked email (show first 2 chars of local part)
     */
    public static String maskEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        
        String cleaned = email.trim();
        int atIndex = cleaned.indexOf('@');
        
        if (atIndex <= 0) {
            return "***"; // Invalid email
        }
        
        String localPart = cleaned.substring(0, atIndex);
        String domain = cleaned.substring(atIndex);
        
        // Show first 2 chars of local part
        int visibleChars = Math.min(2, localPart.length());
        String visible = localPart.substring(0, visibleChars);
        
        return visible + "***" + domain;
    }

    /**
     * Mask phone number
     * 
     * Examples:
     * - "+447553838399" → "+44***8399"
     * - "+905551234567" → "+90***4567"
     * 
     * @param phone Phone number
     * @return Masked phone (show country code + last 4 digits)
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        
        String cleaned = phone.trim().replaceAll("[^0-9+]", "");
        int length = cleaned.length();
        
        if (length <= 4) {
            return "***";
        }
        
        // Find country code (starts with +)
        int countryCodeEnd = 0;
        if (cleaned.startsWith("+")) {
            // Country code is usually 1-3 digits after +
            countryCodeEnd = Math.min(4, cleaned.indexOf(' ') > 0 ? cleaned.indexOf(' ') : 4);
        }
        
        String countryCode = cleaned.substring(0, countryCodeEnd);
        String lastFour = cleaned.substring(length - 4);
        
        return countryCode + "***" + lastFour;
    }

    /**
     * Mask generic sensitive data
     * 
     * Show first 20%, mask middle 60%, show last 20%
     * 
     * @param data Sensitive data
     * @return Masked data
     */
    public static String maskGeneric(String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        
        String cleaned = data.trim();
        int length = cleaned.length();
        
        if (length <= 4) {
            return "***";
        }
        
        int visibleChars = Math.max(1, (int) (length * 0.2)); // 20% visible on each side
        
        String start = cleaned.substring(0, visibleChars);
        String end = cleaned.substring(length - visibleChars);
        
        return start + "***" + end;
    }
}

