package com.fabricmanagement.user_service.util;

import java.util.regex.Pattern;

public final class StringUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[(]?[0-9]{1,4}[)]?[-\\s\\.]?[(]?[0-9]{1,4}[)]?[-\\s\\.]?[0-9]{1,5}[-\\s\\.]?[0-9]{1,5}$"
    );

    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._-]{3,50}$"
    );

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static String trimToNull(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String trimToEmpty(String str) {
        return str == null ? "" : str.trim();
    }

    public static boolean isValidEmail(String email) {
        return isNotBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidPhone(String phone) {
        if (isBlank(phone)) {
            return false;
        }
        String cleaned = phone.replaceAll("[\\s()-]", "");
        return PHONE_PATTERN.matcher(cleaned).matches();
    }

    public static boolean isValidUsername(String username) {
        return isNotBlank(username) && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static String normalizePhone(String phone) {
        if (isBlank(phone)) {
            return null;
        }
        // Remove all non-digit characters except +
        String normalized = phone.replaceAll("[^0-9+]", "");

        // Turkish phone number normalization
        if (normalized.startsWith("0")) {
            normalized = "90" + normalized.substring(1);
        }
        if (!normalized.startsWith("+")) {
            normalized = "+" + normalized;
        }

        return normalized;
    }

    public static String maskEmail(String email) {
        if (!isValidEmail(email)) {
            return email;
        }

        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 3) {
            return localPart.charAt(0) + "***@" + domain;
        }

        return localPart.substring(0, 2) + "***" + localPart.substring(localPart.length() - 1) + "@" + domain;
    }

    public static String maskPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized == null || normalized.length() < 7) {
            return phone;
        }

        int len = normalized.length();
        return normalized.substring(0, len - 7) + " *** **" + normalized.substring(len - 2);
    }

    public static String capitalize(String str) {
        if (isBlank(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public static String generateUsername(String firstName, String lastName) {
        if (isBlank(firstName) && isBlank(lastName)) {
            return null;
        }

        String base = trimToEmpty(firstName).toLowerCase() + "." + trimToEmpty(lastName).toLowerCase();
        return base.replaceAll("[^a-z0-9.]", "").replaceAll("\\.+", ".");
    }
}