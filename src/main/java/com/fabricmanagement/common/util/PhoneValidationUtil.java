package com.fabricmanagement.common.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Phone Validation Utility - Country-specific phone format validation.
 *
 * <p><b>Purpose:</b> Validate phone numbers according to country-specific formats.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>✅ E.164 format validation (base check)</li>
 *   <li>✅ Country-specific format validation</li>
 *   <li>✅ Country code extraction</li>
 *   <li>✅ Phone length validation per country</li>
 * </ul>
 *
 * <p><b>Supported Countries:</b> TR, GB, US, DE, FR, IT, ES, NL, BE, CH, AT, SE, NO, DK, FI, PL, CZ, GR, PT, IE</p>
 */
@UtilityClass
@Slf4j
public class PhoneValidationUtil {

    // E.164 base pattern (international format)
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private static final Pattern LANDLINE_PATTERN = Pattern.compile("^\\+?[0-9\\-()\\s]{6,20}$");

    // Country-specific phone patterns
    private static final Map<String, PhoneFormat> COUNTRY_PATTERNS = new HashMap<>();

    static {
        // Country-specific phone number format patterns
        COUNTRY_PATTERNS.put("TR", new PhoneFormat("^\\+90[5][0-9]{9}$", 13, "Turkey: +90 5XX XXX XX XX"));
        COUNTRY_PATTERNS.put("GB", new PhoneFormat("^\\+44[0-9]{10,11}$", 13, "UK: +44 XXXX XXXXXX"));
        COUNTRY_PATTERNS.put("US", new PhoneFormat("^\\+1[2-9][0-9]{2}[0-9]{7}$", 12, "US: +1 XXX XXX XXXX"));
        COUNTRY_PATTERNS.put("DE", new PhoneFormat("^\\+49[0-9]{10,11}$", 13, "Germany: +49 XXXX XXXXXXX"));
        COUNTRY_PATTERNS.put("FR", new PhoneFormat("^\\+33[1-9][0-9]{8}$", 12, "France: +33 X XX XX XX XX"));
        COUNTRY_PATTERNS.put("IT", new PhoneFormat("^\\+39[0-9]{9,10}$", 13, "Italy: +39 XXX XXXXXXX"));
        COUNTRY_PATTERNS.put("ES", new PhoneFormat("^\\+34[6-9][0-9]{8}$", 12, "Spain: +34 XXX XXX XXX"));
        COUNTRY_PATTERNS.put("NL", new PhoneFormat("^\\+31[6-9][0-9]{8}$", 12, "Netherlands: +31 X XXXX XXXX"));
        COUNTRY_PATTERNS.put("BE", new PhoneFormat("^\\+32[0-9]{9}$", 12, "Belgium: +32 X XXX XX XX"));
        COUNTRY_PATTERNS.put("CH", new PhoneFormat("^\\+41[0-9]{9}$", 12, "Switzerland: +41 XX XXX XX XX"));
        COUNTRY_PATTERNS.put("AT", new PhoneFormat("^\\+43[0-9]{10,11}$", 13, "Austria: +43 XXXX XXXXXX"));
        COUNTRY_PATTERNS.put("SE", new PhoneFormat("^\\+46[0-9]{9}$", 12, "Sweden: +46 X XXX XXX XX"));
        COUNTRY_PATTERNS.put("NO", new PhoneFormat("^\\+47[0-9]{8}$", 11, "Norway: +47 XXXX XXXX"));
        COUNTRY_PATTERNS.put("DK", new PhoneFormat("^\\+45[0-9]{8}$", 11, "Denmark: +45 XX XX XX XX"));
        COUNTRY_PATTERNS.put("FI", new PhoneFormat("^\\+358[0-9]{9,10}$", 13, "Finland: +358 XX XXX XXXX"));
        COUNTRY_PATTERNS.put("PL", new PhoneFormat("^\\+48[0-9]{9}$", 12, "Poland: +48 XXX XXX XXX"));
        COUNTRY_PATTERNS.put("CZ", new PhoneFormat("^\\+420[0-9]{9}$", 13, "Czech Republic: +420 XXX XXX XXX"));
        COUNTRY_PATTERNS.put("GR", new PhoneFormat("^\\+30[0-9]{10}$", 13, "Greece: +30 XXX XXX XXXX"));
        COUNTRY_PATTERNS.put("PT", new PhoneFormat("^\\+351[0-9]{9}$", 13, "Portugal: +351 XXX XXX XXX"));
        COUNTRY_PATTERNS.put("IE", new PhoneFormat("^\\+353[0-9]{9}$", 13, "Ireland: +353 XX XXX XXXX"));
    }

    /**
     * Validate phone number with E.164 format (base check).
     *
     * @param phone Phone number
     * @return true if valid E.164 format
     */
    public static boolean isValidE164(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return E164_PATTERN.matcher(phone.trim()).matches();
    }

    public static ValidationResult validateMobile(String phone) {
        if (phone == null || phone.isBlank()) {
            return ValidationResult.invalid("Mobile number cannot be empty");
        }
        if (!isValidE164(phone)) {
            return ValidationResult.invalid(
                "Invalid mobile format. Must be E.164 (e.g., +905551234567)"
            );
        }
        return ValidationResult.valid();
    }

    public static ValidationResult validateLandline(String phone) {
        if (phone == null || phone.isBlank()) {
            return ValidationResult.invalid("Landline number cannot be empty");
        }
        String trimmed = phone.trim();
        if (!LANDLINE_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.invalid("Invalid landline format. Example: +44 20 7123 4567");
        }
        return ValidationResult.valid();
    }

    /**
     * Validate phone number for specific country.
     *
     * @param phone Phone number (E.164 format)
     * @param countryCode Country code (ISO 3166-1 alpha-2, e.g., "TR", "GB", "US")
     * @return true if valid for the country
     */
    public static boolean isValidForCountry(String phone, String countryCode) {
        if (phone == null || phone.isBlank() || countryCode == null || countryCode.isBlank()) {
            return false;
        }

        // First check E.164 format
        if (!isValidE164(phone)) {
            return false;
        }

        // Get country pattern
        PhoneFormat format = COUNTRY_PATTERNS.get(countryCode.toUpperCase());
        if (format == null) {
            // Country not in our list, use E.164 as fallback
            log.debug("Country {} not in validation list, using E.164 format check", countryCode);
            return true;
        }

        // Validate against country-specific pattern
        boolean matches = Pattern.matches(format.pattern, phone.trim());
        if (!matches) {
            log.debug("Phone {} does not match {} format: {}", phone, countryCode, format.description);
        }
        return matches;
    }

    /**
     * Extract country code from phone number.
     *
     * @param phone Phone number (E.164 format)
     * @return Country code (e.g., "TR", "GB", "US") or null if not found
     */
    public static String extractCountryCode(String phone) {
        if (phone == null || !phone.startsWith("+")) {
            return null;
        }

        // Try to match country code patterns
        for (Map.Entry<String, PhoneFormat> entry : COUNTRY_PATTERNS.entrySet()) {
            if (Pattern.matches(entry.getValue().pattern, phone)) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Validate phone number and return detailed result.
     *
     * @param phone Phone number
     * @param countryCode Country code (optional, for country-specific validation)
     * @return Validation result with error message if invalid
     */
    public static ValidationResult validate(String phone, String countryCode) {
        if (phone == null || phone.isBlank()) {
            return ValidationResult.invalid("Phone number cannot be empty");
        }

        String trimmed = phone.trim();

        // Check E.164 format
        if (!isValidE164(trimmed)) {
            return ValidationResult.invalid(
                "Invalid phone format. Must be E.164 format (e.g., +905551234567)"
            );
        }

        // Country-specific validation if country code provided
        if (countryCode != null && !countryCode.isBlank()) {
            PhoneFormat format = COUNTRY_PATTERNS.get(countryCode.toUpperCase());
            if (format != null && !isValidForCountry(trimmed, countryCode)) {
                return ValidationResult.invalid(
                    String.format("Invalid phone format for %s. Expected: %s", countryCode, format.description)
                );
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Get phone format description for country.
     *
     * @param countryCode Country code
     * @return Format description or null if country not supported
     */
    public static String getFormatDescription(String countryCode) {
        PhoneFormat format = COUNTRY_PATTERNS.get(countryCode != null ? countryCode.toUpperCase() : null);
        return format != null ? format.description : null;
    }

    /**
     * Check if country is supported for validation.
     *
     * @param countryCode Country code
     * @return true if country has specific validation rules
     */
    public static boolean isCountrySupported(String countryCode) {
        return countryCode != null && COUNTRY_PATTERNS.containsKey(countryCode.toUpperCase());
    }

    // Internal class for phone format
    private static class PhoneFormat {
        final String pattern;
        final String description;

        PhoneFormat(String pattern, int expectedLength, String description) {
            this.pattern = pattern;
            this.description = description;
        }
    }

    /**
     * Validation result with error message.
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

