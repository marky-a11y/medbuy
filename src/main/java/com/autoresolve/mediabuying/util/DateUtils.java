package com.autoresolve.mediabuying.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for date/time operations.
 */
public final class DateUtils {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");

    private DateUtils() {
    }

    /**
     * Formats an Instant to ISO-8601 string.
     */
    public static String formatInstant(Instant instant) {
        if (instant == null) return "N/A";
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    /**
     * Formats a LocalDateTime to ISO-8601 string.
     */
    public static String formatLocalDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(ISO_FORMATTER);
    }

    /**
     * Converts LocalDateTime to Instant using the default zone (UTC).
     */
    public static Instant toInstant(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(DEFAULT_ZONE).toInstant();
    }

    /**
     * Checks if an Instant is older than the given number of minutes.
     */
    public static boolean isOlderThan(Instant instant, long minutes) {
        if (instant == null) return true;
        return Duration.between(instant, Instant.now()).toMinutes() > minutes;
    }

    /**
     * Calculates minutes between an Instant and now.
     */
    public static long minutesSince(Instant instant) {
        if (instant == null) return Long.MAX_VALUE;
        return Duration.between(instant, Instant.now()).toMinutes();
    }
}
