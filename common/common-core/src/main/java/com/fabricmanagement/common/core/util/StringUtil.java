package com.fabricmanagement.common.core.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for string operations and validations.
 */
public final class StringUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"
    );

    private StringUtil() {
        // Utility class
    }

    /**
     * Checks if string is null, empty, or contains only whitespace.
     *
     * @param str the string to check
     * @return true if string is blank
     */
    public static boolean isBlank(String str) {
        return StringUtils.isBlank(str);
    }

    /**
     * Checks if string is not null, not empty, and contains non-whitespace characters.
     *
     * @param str the string to check
     * @return true if string is not blank
     */
    public static boolean isNotBlank(String str) {
        return StringUtils.isNotBlank(str);
    }

    /**
     * Capitalizes the first letter of each word.
     *
     * @param str the string to capitalize
     * @return capitalized string
     */
    public static String capitalize(String str) {
        return StringUtils.capitalize(str);
    }

    /**
     * Converts string to camelCase.
     *
     * @param str the string to convert
     * @return camelCase string
     */
    public static String toCamelCase(String str) {
        if (isBlank(str)) {
            return str;
        }

        String[] words = str.toLowerCase().split("[\\s_-]+");
        StringBuilder result = new StringBuilder(words[0]);

        for (int i = 1; i < words.length; i++) {
            result.append(capitalize(words[i]));
        }

        return result.toString();
    }

    /**
     * Converts string to snake_case.
     *
     * @param str the string to convert
     * @return snake_case string
     */
    public static String toSnakeCase(String str) {
        if (isBlank(str)) {
            return str;
        }

        return str.replaceAll("([a-z])([A-Z])", "$1_$2")
                  .replaceAll("[\\s-]+", "_")
                  .toLowerCase();
    }

    /**
     * Validates email format.
     *
     * @param email the email to validate
     * @return true if email is valid
     */
    public static boolean isValidEmail(String email) {
        return isNotBlank(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validates phone number format.
     *
     * @param phone the phone number to validate
     * @return true if phone is valid
     */
    public static boolean isValidPhone(String phone) {
        return isNotBlank(phone) && PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Truncates string to specified length.
     *
     * @param str the string to truncate
     * @param maxLength the maximum length
     * @return truncated string
     */
    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    /**
     * Joins collection of strings with delimiter.
     *
     * @param collection the collection to join
     * @param delimiter the delimiter
     * @return joined string
     */
    public static String join(Collection<String> collection, String delimiter) {
        return collection.stream()
            .filter(StringUtil::isNotBlank)
            .collect(Collectors.joining(delimiter));
    }

    /**
     * Masks sensitive information in string.
     *
     * @param str the string to mask
     * @param visibleChars number of visible characters at start and end
     * @return masked string
     */
    public static String mask(String str, int visibleChars) {
        if (isBlank(str) || str.length() <= visibleChars * 2) {
            return "*".repeat(str != null ? str.length() : 0);
        }

        String start = str.substring(0, visibleChars);
        String end = str.substring(str.length() - visibleChars);
        String middle = "*".repeat(str.length() - visibleChars * 2);

        return start + middle + end;
    }

    /**
     * Generates a slug from string (URL-friendly).
     *
     * @param str the string to convert to slug
     * @return URL-friendly slug
     */
    public static String toSlug(String str) {
        if (isBlank(str)) {
            return "";
        }

        return str.toLowerCase()
                  .replaceAll("[^a-z0-9\\s-]", "")
                  .replaceAll("[\\s-]+", "-")
                  .replaceAll("^-+|-+$", "");
    }
}
