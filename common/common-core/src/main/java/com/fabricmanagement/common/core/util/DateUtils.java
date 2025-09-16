package com.fabricmanagement.common.core.util;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Utility class for date and time operations.
 */
public final class DateUtils {

    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);
    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern(ISO_DATE_TIME_FORMAT);

    private DateUtils() {
        // Utility class
    }

    /**
     * Formats LocalDateTime to string using default format.
     *
     * @param dateTime the date time to format
     * @return formatted string or null if input is null
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DEFAULT_FORMATTER) : null;
    }

    /**
     * Formats LocalDateTime to string using specified format.
     *
     * @param dateTime the date time to format
     * @param formatter the formatter to use
     * @return formatted string or null if input is null
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        return dateTime != null ? dateTime.format(formatter) : null;
    }

    /**
     * Parses string to LocalDateTime using default format.
     *
     * @param dateTimeString the string to parse
     * @return optional LocalDateTime
     */
    public static Optional<LocalDateTime> parse(String dateTimeString) {
        if (StringUtils.isBlank(dateTimeString)) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDateTime.parse(dateTimeString, DEFAULT_FORMATTER));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses string to LocalDateTime using specified format.
     *
     * @param dateTimeString the string to parse
     * @param formatter the formatter to use
     * @return optional LocalDateTime
     */
    public static Optional<LocalDateTime> parse(String dateTimeString, DateTimeFormatter formatter) {
        if (StringUtils.isBlank(dateTimeString)) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDateTime.parse(dateTimeString, formatter));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if first date is after second date.
     *
     * @param first the first date
     * @param second the second date
     * @return true if first is after second
     */
    public static boolean isAfter(LocalDateTime first, LocalDateTime second) {
        return first != null && second != null && first.isAfter(second);
    }

    /**
     * Checks if first date is before second date.
     *
     * @param first the first date
     * @param second the second date
     * @return true if first is before second
     */
    public static boolean isBefore(LocalDateTime first, LocalDateTime second) {
        return first != null && second != null && first.isBefore(second);
    }

    /**
     * Gets current timestamp.
     *
     * @return current LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
