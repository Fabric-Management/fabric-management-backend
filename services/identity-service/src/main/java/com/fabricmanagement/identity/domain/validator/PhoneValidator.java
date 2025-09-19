package com.fabricmanagement.identity.domain.validator;

import java.util.regex.Pattern;

/**
 * Phone number validation utility.
 */
public class PhoneValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"  // E.164 format
    );

    public static boolean isValid(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        String cleaned = phone.replaceAll("[\\s()-]", "");
        return PHONE_PATTERN.matcher(cleaned).matches();
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[\\s()-]", "");
    }
}