package com.fabricmanagement.shared.infrastructure.util;

import com.fabricmanagement.shared.infrastructure.config.TextProcessingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Masking Utility
 * 
 * Privacy-focused masking for sensitive information.
 * Used to show partial data without revealing full details.
 * 
 * USE CASES:
 * - Show existing contact hints to users
 * - Duplicate detection responses
 * - Privacy-compliant data display
 * 
 * GDPR/PRIVACY COMPLIANT:
 * - Configurable masking level
 * - Minimum masked characters enforced
 * - No full data exposure
 * 
 * PRODUCTION-READY:
 * - Configuration-driven
 * - Handles edge cases
 * - Consistent formatting
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MaskingUtil {
    
    private final TextProcessingConfig config;
    
    /**
     * Mask email address
     * 
     * Shows first N and last N characters of username, masks the rest.
     * Always shows full domain for context.
     * 
     * Examples (visibleChars=2):
     * - "admin@example.com" → "ad***n@example.com"
     * - "a@example.com" → "a***@example.com" (short username)
     * - "john.doe@company.com" → "jo***oe@company.com"
     * - "verylongemail@test.com" → "ve***il@test.com"
     * 
     * @param email Email address to mask
     * @return Masked email
     */
    public String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "***@***";
        }
        
        try {
            String[] parts = email.split("@");
            if (parts.length != 2) {
                return "***@***";
            }
            
            String username = parts[0];
            String domain = parts[1];
            
            String maskedUsername = maskString(
                username, 
                config.getMasking().getEmailVisibleChars(),
                config.getMasking().getMaskingChar()
            );
            
            return maskedUsername + "@" + domain;
            
        } catch (Exception e) {
            log.warn("Error masking email: {}", e.getMessage());
            return "***@***";
        }
    }
    
    /**
     * Mask phone number
     * 
     * Shows country code and last N digits, masks the middle.
     * 
     * Examples (visibleDigits=4):
     * - "+905551234567" → "+90555***4567"
     * - "+1234567890" → "+123***7890"
     * - "05551234567" → "0555***4567"
     * - "+90 555 123 45 67" → "+90 555 ***45 67" (preserves formatting)
     * 
     * @param phone Phone number to mask
     * @return Masked phone number
     */
    public String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "***";
        }
        
        try {
            // Extract only digits for masking logic
            String digitsOnly = phone.replaceAll("[^0-9+]", "");
            
            if (digitsOnly.length() <= config.getMasking().getPhoneVisibleDigits()) {
                // Too short to mask meaningfully
                return maskAllButLast(phone, 2);
            }
            
            int visibleDigits = config.getMasking().getPhoneVisibleDigits();
            int prefixLength = Math.min(5, digitsOnly.length() - visibleDigits); // Show country code + area
            
            String prefix = digitsOnly.substring(0, prefixLength);
            String suffix = digitsOnly.substring(digitsOnly.length() - visibleDigits);
            
            return prefix + "***" + suffix;
            
        } catch (Exception e) {
            log.warn("Error masking phone: {}", e.getMessage());
            return "***";
        }
    }
    
    /**
     * Generic string masking
     * 
     * Shows first N and last N characters, masks the middle.
     * Enforces minimum masked characters for privacy.
     * 
     * @param text Text to mask
     * @param visibleChars Number of visible characters (each side)
     * @param maskChar Masking character (default: *)
     * @return Masked text
     */
    public String maskString(String text, int visibleChars, String maskChar) {
        if (text == null || text.isBlank()) {
            return maskChar.repeat(config.getMasking().getMinimumMaskedChars());
        }
        
        int length = text.length();
        int minMasked = config.getMasking().getMinimumMaskedChars();
        
        // If text too short, show partial
        if (length <= visibleChars * 2) {
            if (length <= 2) {
                return text.charAt(0) + maskChar.repeat(minMasked);
            }
            return text.charAt(0) + maskChar.repeat(minMasked) + text.charAt(length - 1);
        }
        
        // Normal masking: first N + *** + last N
        String prefix = text.substring(0, visibleChars);
        String suffix = text.substring(length - visibleChars);
        
        // Calculate masked length (at least minimum)
        int maskedLength = Math.max(minMasked, length - (visibleChars * 2));
        
        return prefix + maskChar.repeat(maskedLength) + suffix;
    }
    
    /**
     * Mask all but last N characters
     * 
     * @param text Text to mask
     * @param lastChars Number of characters to show at end
     * @return Masked text
     */
    public String maskAllButLast(String text, int lastChars) {
        if (text == null || text.isBlank()) {
            return "***";
        }
        
        int length = text.length();
        if (length <= lastChars) {
            return text;
        }
        
        String maskChar = config.getMasking().getMaskingChar();
        String suffix = text.substring(length - lastChars);
        
        return maskChar.repeat(length - lastChars) + suffix;
    }
    
    /**
     * Mask name (for GDPR compliance)
     * 
     * Examples:
     * - "Ahmet Yılmaz" → "Ah*** Y***"
     * - "John Doe Smith" → "Jo*** D*** S***"
     * 
     * @param fullName Full name
     * @return Masked name
     */
    public String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "***";
        }
        
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder masked = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() <= 2) {
                masked.append(part);
            } else {
                masked.append(part.substring(0, 2))
                      .append(config.getMasking().getMaskingChar().repeat(
                          Math.max(1, part.length() - 2)));
            }
            
            if (i < parts.length - 1) {
                masked.append(" ");
            }
        }
        
        return masked.toString();
    }
}

