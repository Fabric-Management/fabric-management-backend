package com.fabricmanagement.common.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for masking Personally Identifiable Information (PII) in logs.
 *
 * <p>GDPR/KVKK compliance - sensitive data should be masked in production logs.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * log.info("User registered: {}", PiiMaskingUtil.maskEmail(email));
 * log.info("Phone verification: {}", PiiMaskingUtil.maskPhone(phone));
 * </pre>
 */
@UtilityClass
@Slf4j
public class PiiMaskingUtil {

    private static final String MASK = "***";
    private static final boolean MASKING_ENABLED = !isLocalProfile();

    /**
     * Mask email address for logging.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>john.doe@example.com → jo***@example.com</li>
     *   <li>a@test.com → a***@test.com</li>
     * </ul>
     *
     * @param email Email address to mask
     * @return Masked email or original if masking disabled
     */
    public static String maskEmail(String email) {
        if (!MASKING_ENABLED || email == null || email.isBlank()) {
            return email;
        }

        if (!email.contains("@")) {
            return MASK;
        }

        String[] parts = email.split("@");
        if (parts.length != 2) {
            return MASK;
        }

        String localPart = parts[0];
        String domain = parts[1];

        // Show first 2 chars, mask the rest
        String maskedLocal = localPart.length() <= 2
            ? localPart
            : localPart.substring(0, 2) + MASK;

        return maskedLocal + "@" + domain;
    }

    /**
     * Mask phone number for logging.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>+905551234567 → +905***4567</li>
     *   <li>05551234567 → 055***4567</li>
     * </ul>
     *
     * @param phone Phone number to mask
     * @return Masked phone or original if masking disabled
     */
    public static String maskPhone(String phone) {
        if (!MASKING_ENABLED || phone == null || phone.isBlank()) {
            return phone;
        }

        if (phone.length() <= 7) {
            return MASK;
        }

        // Show first 3 and last 4 digits
        String prefix = phone.substring(0, 3);
        String suffix = phone.substring(phone.length() - 4);

        return prefix + MASK + suffix;
    }

    /**
     * Mask credit card number for logging.
     *
     * <p>Example: 1234567890123456 → 1234***3456</p>
     *
     * @param cardNumber Card number to mask
     * @return Masked card number or original if masking disabled
     */
    public static String maskCardNumber(String cardNumber) {
        if (!MASKING_ENABLED || cardNumber == null || cardNumber.isBlank()) {
            return cardNumber;
        }

        if (cardNumber.length() <= 8) {
            return MASK;
        }

        // Show first 4 and last 4 digits (PCI DSS compliant)
        String prefix = cardNumber.substring(0, 4);
        String suffix = cardNumber.substring(cardNumber.length() - 4);

        return prefix + MASK + suffix;
    }

    /**
     * Mask generic sensitive data.
     *
     * <p>Shows only first and last char.</p>
     *
     * @param sensitive Sensitive data to mask
     * @return Masked data or original if masking disabled
     */
    public static String mask(String sensitive) {
        if (!MASKING_ENABLED || sensitive == null || sensitive.isBlank()) {
            return sensitive;
        }

        if (sensitive.length() <= 2) {
            return MASK;
        }

        return sensitive.charAt(0) + MASK + sensitive.charAt(sensitive.length() - 1);
    }

    /**
     * Check if masking is enabled.
     *
     * <p>Masking is DISABLED in local/dev profiles for debugging.</p>
     * <p>Masking is ENABLED in production.</p>
     *
     * @return true if masking is enabled
     */
    public static boolean isMaskingEnabled() {
        return MASKING_ENABLED;
    }

    /**
     * Detect if running in local profile.
     *
     * @return true if local profile is active
     */
    private static boolean isLocalProfile() {
        String activeProfile = System.getProperty("spring.profiles.active");
        if (activeProfile == null) {
            activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        return activeProfile != null && activeProfile.contains("local");
    }
}

