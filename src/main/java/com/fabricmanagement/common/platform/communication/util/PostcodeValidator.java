package com.fabricmanagement.common.platform.communication.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Postcode format validator based on country-specific patterns.
 *
 * <p>Validates postcode formats before making Google Maps API calls to reduce unnecessary requests.</p>
 */
@Component
@Slf4j
public class PostcodeValidator {

    // UK Postcode patterns (most common formats)
    // Examples: MK1 1QB, SW1A 1AA, M1 1AA, B33 8TH, W1A 0AX
    private static final Pattern UK_POSTCODE = Pattern.compile(
        "^[A-Z]{1,2}[0-9][A-Z0-9]?\\s?[0-9][A-Z]{2}$",
        Pattern.CASE_INSENSITIVE
    );

    // Turkey Postcode pattern (5 digits)
    // Example: 34000, 06100
    private static final Pattern TR_POSTCODE = Pattern.compile("^[0-9]{5}$");

    // US ZIP code patterns (5 digits or ZIP+4: 12345-6789)
    private static final Pattern US_ZIP = Pattern.compile("^[0-9]{5}(-[0-9]{4})?$");

    // Canada Postal Code (A1A 1A1 format)
    private static final Pattern CA_POSTCODE = Pattern.compile("^[A-Z][0-9][A-Z]\\s?[0-9][A-Z][0-9]$", Pattern.CASE_INSENSITIVE);

    // Germany Postcode (5 digits)
    private static final Pattern DE_POSTCODE = Pattern.compile("^[0-9]{5}$");

    // France Postcode (5 digits)
    private static final Pattern FR_POSTCODE = Pattern.compile("^[0-9]{5}$");

    // Italy Postcode (5 digits)
    private static final Pattern IT_POSTCODE = Pattern.compile("^[0-9]{5}$");

    // Spain Postcode (5 digits)
    private static final Pattern ES_POSTCODE = Pattern.compile("^[0-9]{5}$");

    // Netherlands Postcode (4 digits + 2 letters: 1234 AB)
    private static final Pattern NL_POSTCODE = Pattern.compile("^[0-9]{4}\\s?[A-Z]{2}$", Pattern.CASE_INSENSITIVE);

    // Australia Postcode (4 digits)
    private static final Pattern AU_POSTCODE = Pattern.compile("^[0-9]{4}$");

    /**
     * Validates postcode format based on country code (ISO 3166-1 alpha-2).
     *
     * @param postcode Postcode to validate
     * @param countryCode ISO 3166-1 alpha-2 country code (e.g., "GB", "TR", "US")
     * @return true if format is valid or country is not supported (allows validation)
     */
    public boolean isValidFormat(String postcode, String countryCode) {
        if (postcode == null || postcode.isBlank()) {
            return false;
        }

        String normalized = postcode.trim().replaceAll("\\s+", " "); // Normalize whitespace

        if (countryCode == null || countryCode.isBlank()) {
            // No country specified - use minimum length check (at least 3 characters)
            return normalized.length() >= 3;
        }

        String upperCountry = countryCode.toUpperCase();

        return switch (upperCountry) {
            case "GB", "UK" -> UK_POSTCODE.matcher(normalized).matches() && normalized.length() >= 5;
            case "TR" -> TR_POSTCODE.matcher(normalized).matches() && normalized.length() == 5;
            case "US" -> US_ZIP.matcher(normalized).matches() && normalized.length() >= 5;
            case "CA" -> CA_POSTCODE.matcher(normalized).matches() && normalized.length() >= 6;
            case "DE" -> DE_POSTCODE.matcher(normalized).matches() && normalized.length() == 5;
            case "FR" -> FR_POSTCODE.matcher(normalized).matches() && normalized.length() == 5;
            case "IT" -> IT_POSTCODE.matcher(normalized).matches() && normalized.length() == 5;
            case "ES" -> ES_POSTCODE.matcher(normalized).matches() && normalized.length() == 5;
            case "NL" -> NL_POSTCODE.matcher(normalized).matches() && normalized.length() >= 6;
            case "AU" -> AU_POSTCODE.matcher(normalized).matches() && normalized.length() == 4;
            default -> {
                // Unknown country - use minimum length check (at least 4 characters for most countries)
                log.debug("Unknown country code for postcode validation: {}. Using minimum length check (4 chars)", countryCode);
                yield normalized.length() >= 4;
            }
        };
    }

    /**
     * Gets minimum postcode length for a country.
     *
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return Minimum required length
     */
    public int getMinimumLength(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return 3; // Default minimum for global search
        }

        String upperCountry = countryCode.toUpperCase();

        return switch (upperCountry) {
            case "GB", "UK" -> 5; // MK1 1QB = 7, but allow partial (MK1 = 3, but we require 5 for validation)
            case "TR", "DE", "FR", "IT", "ES" -> 5;
            case "US" -> 5; // ZIP code
            case "CA" -> 6; // A1A 1A1
            case "NL" -> 6; // 1234 AB
            case "AU" -> 4;
            default -> 4; // Default minimum for unknown countries
        };
    }

    /**
     * Checks if postcode has minimum length to make API call worthwhile.
     *
     * @param postcode Postcode to check
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return true if postcode is long enough to search
     */
    public boolean isLongEnoughToSearch(String postcode, String countryCode) {
        if (postcode == null || postcode.isBlank()) {
            return false;
        }

        int minLength = getMinimumLength(countryCode);
        String normalized = postcode.trim();
        
        return normalized.length() >= minLength;
    }
}

