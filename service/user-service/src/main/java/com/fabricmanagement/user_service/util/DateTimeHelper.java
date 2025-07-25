package com.fabricmanagement.user_service.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DateTimeHelper {

    // Common formatters
    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateTimeHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Get current UTC time
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Get current time in specific timezone
     */
    public static LocalDateTime nowInZone(String timezone) {
        return LocalDateTime.now(ZoneId.of(timezone));
    }

    /**
     * Convert UTC time to specific timezone
     */
    public static LocalDateTime toZone(LocalDateTime utcTime, String timezone) {
        if (utcTime == null || timezone == null) {
            return utcTime;
        }
        return utcTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of(timezone))
                .toLocalDateTime();
    }

    /**
     * Convert from timezone to UTC
     */
    public static LocalDateTime toUTC(LocalDateTime localTime, String timezone) {
        if (localTime == null || timezone == null) {
            return localTime;
        }
        return localTime.atZone(ZoneId.of(timezone))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * Start of day in UTC
     */
    public static LocalDateTime startOfDay() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay();
    }

    /**
     * Start of day in specific timezone
     */
    public static LocalDateTime startOfDay(String timezone) {
        return LocalDate.now(ZoneId.of(timezone)).atStartOfDay();
    }

    /**
     * End of day in UTC
     */
    public static LocalDateTime endOfDay() {
        return LocalDate.now(ZoneOffset.UTC).atTime(LocalTime.MAX);
    }

    /**
     * End of day in specific timezone
     */
    public static LocalDateTime endOfDay(String timezone) {
        return LocalDate.now(ZoneId.of(timezone)).atTime(LocalTime.MAX);
    }

    /**
     * Start of month in UTC
     */
    public static LocalDateTime startOfMonth() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay();
    }

    /**
     * End of month in UTC
     */
    public static LocalDateTime endOfMonth() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return now.withDayOfMonth(now.lengthOfMonth()).atTime(LocalTime.MAX);
    }

    /**
     * Calculate time difference between two dates
     */
    public static Duration getDuration(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return Duration.ZERO;
        }
        return Duration.between(from, to);
    }

    /**
     * Get relative time text (locale-aware)
     */
    public static String getRelativeTime(LocalDateTime dateTime, Locale locale) {
        if (dateTime == null) {
            return getLocalizedText("unknown", locale);
        }

        LocalDateTime now = now();
        Duration duration = Duration.between(dateTime, now);
        long minutes = duration.toMinutes();
        long absMinutes = Math.abs(minutes);

        if (absMinutes < 1) {
            return getLocalizedText("now", locale);
        } else if (absMinutes < 60) {
            return formatRelativeTime(minutes, "minute", locale);
        } else if (absMinutes < 1440) {
            return formatRelativeTime(minutes / 60, "hour", locale);
        } else if (absMinutes < 43200) {
            return formatRelativeTime(minutes / 1440, "day", locale);
        } else {
            return formatDate(dateTime, locale);
        }
    }

    /**
     * Format relative time with locale
     */
    private static String formatRelativeTime(long value, String unit, Locale locale) {
        long absValue = Math.abs(value);
        String direction = value > 0 ? "ago" : "later";

        // For now, simple English/Turkish support
        if (locale.getLanguage().equals("tr")) {
            String turkishUnit = switch (unit) {
                case "minute" -> "dakika";
                case "hour" -> "saat";
                case "day" -> "gün";
                default -> unit;
            };
            return value > 0 ? absValue + " " + turkishUnit + " önce"
                    : absValue + " " + turkishUnit + " sonra";
        }

        // Default to English
        String plural = absValue != 1 ? "s" : "";
        return value > 0 ? absValue + " " + unit + plural + " ago"
                : "in " + absValue + " " + unit + plural;
    }

    /**
     * Get localized text
     */
    private static String getLocalizedText(String key, Locale locale) {
        if (locale.getLanguage().equals("tr")) {
            return switch (key) {
                case "unknown" -> "Bilinmiyor";
                case "now" -> "Şimdi";
                default -> key;
            };
        }
        return switch (key) {
            case "unknown" -> "Unknown";
            case "now" -> "Now";
            default -> key;
        };
    }

    /**
     * Format date based on locale
     */
    private static String formatDate(LocalDateTime dateTime, Locale locale) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                locale.getLanguage().equals("tr") ? "dd.MM.yyyy HH:mm" : "MM/dd/yyyy HH:mm",
                locale
        );
        return dateTime.format(formatter);
    }

    /**
     * Verification token expire time
     */
    public static LocalDateTime getVerificationExpireTime(int minutes) {
        return now().plusMinutes(minutes);
    }

    /**
     * Password reset token expire time
     */
    public static LocalDateTime getPasswordResetExpireTime() {
        return now().plusHours(1);
    }

    /**
     * Check if same day
     */
    public static boolean isSameDay(LocalDateTime date1, LocalDateTime date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        return date1.toLocalDate().equals(date2.toLocalDate());
    }

    /**
     * Check if today (in UTC)
     */
    public static boolean isToday(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        return isSameDay(dateTime, now());
    }

    /**
     * Check if today in specific timezone
     */
    public static boolean isTodayInZone(LocalDateTime utcDateTime, String timezone) {
        if (utcDateTime == null) {
            return false;
        }
        LocalDateTime localTime = toZone(utcDateTime, timezone);
        LocalDateTime localNow = nowInZone(timezone);
        return isSameDay(localTime, localNow);
    }

    /**
     * Check if past
     */
    public static boolean isPast(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        return dateTime.isBefore(now());
    }

    /**
     * Check if future
     */
    public static boolean isFuture(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        return dateTime.isAfter(now());
    }

    /**
     * Check if between dates
     */
    public static boolean isBetween(LocalDateTime dateTime, LocalDateTime start, LocalDateTime end) {
        if (dateTime == null) {
            return false;
        }
        boolean afterStart = start == null || !dateTime.isBefore(start);
        boolean beforeEnd = end == null || !dateTime.isAfter(end);
        return afterStart && beforeEnd;
    }

    /**
     * Check if working hours (in specific timezone)
     */
    public static boolean isWorkingHours(LocalDateTime utcDateTime, String timezone, int startHour, int endHour) {
        if (utcDateTime == null) {
            return false;
        }
        LocalDateTime localTime = toZone(utcDateTime, timezone);
        int hour = localTime.getHour();
        return hour >= startHour && hour < endHour;
    }

    /**
     * Check if weekend (in specific timezone)
     */
    public static boolean isWeekend(LocalDateTime utcDateTime, String timezone) {
        if (utcDateTime == null) {
            return false;
        }
        LocalDateTime localTime = toZone(utcDateTime, timezone);
        DayOfWeek day = localTime.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /**
     * Format for API response (ISO 8601 with Z)
     */
    public static String toApiResponse(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneOffset.UTC).format(ISO_INSTANT_FORMATTER);
    }

    /**
     * Parse from API request
     */
    public static LocalDateTime fromApiRequest(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        // Handle both with and without Z
        if (dateTimeStr.endsWith("Z")) {
            return Instant.parse(dateTimeStr).atZone(ZoneOffset.UTC).toLocalDateTime();
        }
        return LocalDateTime.parse(dateTimeStr);
    }

    /**
     * Convert to Unix timestamp
     */
    public static long toUnixTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    /**
     * Convert from Unix timestamp
     */
    public static LocalDateTime fromUnixTimestamp(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);
    }

    /**
     * Get timezone offset in hours
     */
    public static int getTimezoneOffset(String timezone) {
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return now.getOffset().getTotalSeconds() / 3600;
    }

    /**
     * Validate timezone string
     */
    public static boolean isValidTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}