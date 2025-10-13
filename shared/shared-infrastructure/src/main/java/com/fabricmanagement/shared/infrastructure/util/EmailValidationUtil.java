package com.fabricmanagement.shared.infrastructure.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Email Validation Utility
 * 
 * Advanced email validation for business rules.
 * Distinguishes between corporate and personal emails.
 * 
 * USE CASES:
 * - Tenant registration: Require corporate email
 * - User profile updates: Allow personal email
 * - Email domain matching: Prevent typos/fraud
 * 
 * PRODUCTION-READY:
 * - RFC 5322 compliant validation
 * - Free email provider detection
 * - Domain extraction
 * - Corporate email verification
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailValidationUtil {
    
    // RFC 5322 compliant email regex (simplified)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$"
    );
    
    // Common free email providers (personal emails)
    private static final List<String> FREE_EMAIL_PROVIDERS = Arrays.asList(
        // Global providers
        "gmail.com", "yahoo.com", "outlook.com", "hotmail.com", 
        "live.com", "msn.com", "aol.com", "icloud.com",
        "mail.com", "protonmail.com", "zoho.com",
        
        // Turkish providers
        "yandex.com.tr", "yandex.com", "mynet.com", "turk.net",
        "superonline.com", "ttmail.com", "windowslive.com",
        
        // German providers
        "gmx.de", "web.de", "t-online.de",
        
        // French providers
        "orange.fr", "wanadoo.fr", "free.fr",
        
        // Spanish providers
        "terra.es", "ya.com"
    );
    
    /**
     * Validate email format (RFC 5322)
     * 
     * @param email Email address
     * @return true if valid format
     */
    public boolean isValidFormat(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
    
    /**
     * Check if email is a corporate email (not free provider)
     * 
     * Corporate email examples:
     * - admin@acmetekstil.com ✅
     * - finance@company.co.uk ✅
     * 
     * Personal email examples:
     * - user@gmail.com ❌
     * - someone@yahoo.com ❌
     * - test@yandex.com.tr ❌
     * 
     * @param email Email address
     * @return true if corporate email
     */
    public boolean isCorporateEmail(String email) {
        if (!isValidFormat(email)) {
            return false;
        }
        
        String domain = extractDomain(email);
        if (domain == null) {
            return false;
        }
        
        // Check if domain is in free email providers list
        return !isFreeEmailProvider(domain);
    }
    
    /**
     * Check if email domain is a free email provider
     * 
     * @param domain Email domain
     * @return true if free provider
     */
    public boolean isFreeEmailProvider(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        
        String lowerDomain = domain.toLowerCase().trim();
        
        // Check exact match
        if (FREE_EMAIL_PROVIDERS.contains(lowerDomain)) {
            return true;
        }
        
        // Check if subdomain of free provider (e.g., mail.yahoo.com)
        for (String provider : FREE_EMAIL_PROVIDERS) {
            if (lowerDomain.endsWith("." + provider)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extract domain from email address
     * 
     * Examples:
     * - "admin@acmetekstil.com" → "acmetekstil.com"
     * - "user@mail.company.co.uk" → "mail.company.co.uk"
     * 
     * @param email Email address
     * @return Domain part, or null if invalid
     */
    public String extractDomain(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return null;
        }
        
        return parts[1].toLowerCase().trim();
    }
    
    /**
     * Extract base domain (without subdomains)
     * 
     * Examples:
     * - "acmetekstil.com" → "acmetekstil.com"
     * - "mail.acmetekstil.com" → "acmetekstil.com"
     * - "company.co.uk" → "company.co.uk" (handles ccTLD)
     * 
     * @param domain Full domain
     * @return Base domain
     */
    public String extractBaseDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        
        String[] parts = domain.split("\\.");
        
        // Handle ccTLD (e.g., co.uk, com.tr)
        if (parts.length >= 3 && isCountryCodeTLD(parts[parts.length - 2])) {
            return parts[parts.length - 3] + "." + parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        
        // Standard domain
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        
        return domain;
    }
    
    /**
     * Check if TLD part is a country code (co, com, gov, etc.)
     */
    private boolean isCountryCodeTLD(String tld) {
        List<String> ccTlds = Arrays.asList("co", "com", "gov", "net", "org", "edu");
        return ccTlds.contains(tld.toLowerCase());
    }
    
    /**
     * Check if two email domains match (considering company domain)
     * 
     * Examples:
     * - "admin@acmetekstil.com" vs "acmetekstil.com" → MATCH
     * - "finance@acmetekstil.com" vs "acmetekstil.com" → MATCH
     * - "user@gmail.com" vs "acmetekstil.com" → NO MATCH
     * 
     * @param email Email address
     * @param companyDomain Expected company domain (from website)
     * @return true if email domain matches company domain
     */
    public boolean emailMatchesCompanyDomain(String email, String companyDomain) {
        if (email == null || companyDomain == null) {
            return false;
        }
        
        String emailDomain = extractDomain(email);
        if (emailDomain == null) {
            return false;
        }
        
        // Clean company domain (remove protocol, path, etc.)
        String cleanCompanyDomain = cleanDomain(companyDomain);
        
        // Extract base domains for comparison
        String emailBase = extractBaseDomain(emailDomain);
        String companyBase = extractBaseDomain(cleanCompanyDomain);
        
        return emailBase != null && emailBase.equals(companyBase);
    }
    
    /**
     * Clean domain from URL
     * 
     * Examples:
     * - "https://acmetekstil.com" → "acmetekstil.com"
     * - "www.acmetekstil.com" → "acmetekstil.com"
     * - "acmetekstil.com/about" → "acmetekstil.com"
     * 
     * @param url URL or domain
     * @return Clean domain
     */
    public String cleanDomain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        
        String cleaned = url.toLowerCase().trim();
        
        // Remove protocol
        cleaned = cleaned.replaceAll("^https?://", "");
        cleaned = cleaned.replaceAll("^www\\.", "");
        
        // Remove path
        if (cleaned.contains("/")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("/"));
        }
        
        // Remove port
        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(0, cleaned.indexOf(":"));
        }
        
        return cleaned;
    }
    
    /**
     * Get user-friendly error message for invalid corporate email
     * 
     * @param email The email address
     * @return Error message
     */
    public String getCorporateEmailErrorMessage(String email) {
        String domain = extractDomain(email);
        
        if (isFreeEmailProvider(domain)) {
            return String.format(
                "Please use your corporate email address. Personal email providers (%s) are not allowed for company registration.",
                domain
            );
        }
        
        return "Invalid email format. Please use a valid corporate email address.";
    }
}

