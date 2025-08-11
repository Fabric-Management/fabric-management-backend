package com.fabricmanagement.user_service.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class PasswordHelper {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL_CHARS;

    private static final Pattern HAS_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern HAS_SPECIAL = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*");

    private PasswordHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    public static String generateSecurePassword() {
        return generateSecurePassword(12);
    }

    public static String generateSecurePassword(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Password length must be at least 8");
        }

        List<Character> password = new ArrayList<>();

        // Ensure at least one of each type
        password.add(UPPERCASE.charAt(RANDOM.nextInt(UPPERCASE.length())));
        password.add(LOWERCASE.charAt(RANDOM.nextInt(LOWERCASE.length())));
        password.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        password.add(SPECIAL_CHARS.charAt(RANDOM.nextInt(SPECIAL_CHARS.length())));

        // Fill the rest
        for (int i = 4; i < length; i++) {
            password.add(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
        }

        // Shuffle to avoid predictable patterns
        Collections.shuffle(password, RANDOM);

        return password.stream()
                .map(String::valueOf)
                .reduce("", String::concat);
    }

    public static PasswordStrength checkStrength(String password) {
        if (password == null || password.length() < 6) {
            return PasswordStrength.VERY_WEAK;
        }

        int score = 0;

        // Length score
        if (password.length() >= 8) score++;
        if (password.length() >= 10) score++;
        if (password.length() >= 12) score++;

        // Character variety score
        if (HAS_UPPERCASE.matcher(password).matches()) score++;
        if (HAS_LOWERCASE.matcher(password).matches()) score++;
        if (HAS_DIGIT.matcher(password).matches()) score++;
        if (HAS_SPECIAL.matcher(password).matches()) score++;

        // Common patterns check (deduct points)
        if (hasCommonPattern(password)) score -= 2;

        return PasswordStrength.fromScore(Math.max(0, score));
    }

    private static boolean hasCommonPattern(String password) {
        String lower = password.toLowerCase();
        return lower.contains("password") ||
                lower.contains("123456") ||
                lower.contains("qwerty") ||
                lower.contains("admin") ||
                lower.matches(".*(.)(\\1{2,}).*"); // Repeated characters
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        return HAS_UPPERCASE.matcher(password).matches() &&
                HAS_LOWERCASE.matcher(password).matches() &&
                HAS_DIGIT.matcher(password).matches();
    }

    public static ValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("Şifre boş olamaz");
            return new ValidationResult(false, errors);
        }

        if (password.length() < 8) {
            errors.add("Şifre en az 8 karakter olmalıdır");
        }

        if (!HAS_UPPERCASE.matcher(password).matches()) {
            errors.add("En az bir büyük harf içermelidir");
        }

        if (!HAS_LOWERCASE.matcher(password).matches()) {
            errors.add("En az bir küçük harf içermelidir");
        }

        if (!HAS_DIGIT.matcher(password).matches()) {
            errors.add("En az bir rakam içermelidir");
        }

        if (hasCommonPattern(password)) {
            errors.add("Çok yaygın bir şifre deseni kullanıyorsunuz");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public enum PasswordStrength {
        VERY_WEAK("Çok Zayıf", "red"),
        WEAK("Zayıf", "orange"),
        MEDIUM("Orta", "yellow"),
        STRONG("Güçlü", "lightgreen"),
        VERY_STRONG("Çok Güçlü", "green");

        private final String description;
        private final String color;

        PasswordStrength(String description, String color) {
            this.description = description;
            this.color = color;
        }

        public String getDescription() {
            return description;
        }

        public String getColor() {
            return color;
        }

        static PasswordStrength fromScore(int score) {
            if (score <= 1) return VERY_WEAK;
            if (score <= 3) return WEAK;
            if (score <= 5) return MEDIUM;
            if (score <= 6) return STRONG;
            return VERY_STRONG;
        }
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public String getErrorMessage() {
            return valid ? "" : String.join(", ", errors);
        }
    }
}