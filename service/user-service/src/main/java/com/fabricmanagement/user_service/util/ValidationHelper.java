package com.fabricmanagement.user_service.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ValidationHelper {

    private ValidationHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Null değilse işlemi uygula
     */
    public static <T, R> R applyIfNotNull(T value, Function<T, R> function) {
        return value != null ? function.apply(value) : null;
    }

    /**
     * Null değilse işlemi uygula, null ise default değer dön
     */
    public static <T, R> R applyIfNotNull(T value, Function<T, R> function, R defaultValue) {
        return value != null ? function.apply(value) : defaultValue;
    }

    /**
     * String boş değilse işlemi uygula
     */
    public static <R> R applyIfNotBlank(String value, Function<String, R> function) {
        return StringUtils.isNotBlank(value) ? function.apply(value.trim()) : null;
    }

    /**
     * Collection boş değilse işlemi uygula
     */
    public static <T, R> R applyIfNotEmpty(Collection<T> collection, Function<Collection<T>, R> function) {
        return collection != null && !collection.isEmpty() ? function.apply(collection) : null;
    }

    /**
     * Koşul sağlanıyorsa değeri dön, yoksa null
     */
    public static <T> T returnIfTrue(boolean condition, T value) {
        return condition ? value : null;
    }

    /**
     * Koşul sağlanıyorsa değeri dön, yoksa default
     */
    public static <T> T returnIfTrue(boolean condition, T value, T defaultValue) {
        return condition ? value : defaultValue;
    }

    /**
     * Lazy evaluation ile koşul sağlanıyorsa değeri dön
     */
    public static <T> T returnIfTrue(boolean condition, Supplier<T> supplier) {
        return condition ? supplier.get() : null;
    }

    /**
     * Null-safe equals
     */
    public static boolean safeEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Null-safe string equals (case insensitive)
     */
    public static boolean safeEqualsIgnoreCase(String a, String b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    /**
     * UUID validation
     */
    public static boolean isValidUUID(String uuid) {
        if (StringUtils.isBlank(uuid)) {
            return false;
        }
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Age validation
     */
    public static boolean isValidAge(LocalDate birthDate, int minAge, int maxAge) {
        if (birthDate == null) {
            return false;
        }
        int age = LocalDate.now().getYear() - birthDate.getYear();
        return age >= minAge && age <= maxAge;
    }

    /**
     * Date range validation
     */
    public static boolean isInDateRange(LocalDateTime date, LocalDateTime start, LocalDateTime end) {
        if (date == null) {
            return false;
        }
        boolean afterStart = start == null || !date.isBefore(start);
        boolean beforeEnd = end == null || !date.isAfter(end);
        return afterStart && beforeEnd;
    }

    /**
     * Null veya boş kontrolü - hepsi dolu mu?
     */
    @SafeVarargs
    public static <T> boolean allNotNull(T... values) {
        if (values == null) return false;
        for (T value : values) {
            if (value == null) return false;
            if (value instanceof String && StringUtils.isBlank((String) value)) {
                return false;
            }
            if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * En az biri dolu mu?
     */
    @SafeVarargs
    public static <T> boolean anyNotNull(T... values) {
        if (values == null) return false;
        for (T value : values) {
            if (value != null) {
                if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                    return true;
                } else if (value instanceof Collection && !((Collection<?>) value).isEmpty()) {
                    return true;
                } else if (!(value instanceof String) && !(value instanceof Collection)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Require non-null with custom message
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /**
     * Require condition with custom message
     */
    public static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Validate and transform
     */
    public static <T, R> R validateAndTransform(T value, Function<T, Boolean> validator,
                                                Function<T, R> transformer, String errorMessage) {
        if (value == null || !validator.apply(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return transformer.apply(value);
    }
}