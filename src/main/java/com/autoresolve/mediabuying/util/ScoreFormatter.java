package com.autoresolve.mediabuying.util;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility class for formatting scores, currency, and percentages for display.
 */
public final class ScoreFormatter {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    private ScoreFormatter() {
    }

    /**
     * Formats a numeric score for display:
     * - Scores >= 0.01: Rounded to 1 decimal place
     * - Scores < 0.01: Formatted as "0.0X" for visibility
     * - Null values: Returns "N/A"
     */
    public static String formatScore(Double score) {
        if (score == null) return "N/A";
        if (Math.abs(score - Math.round(score)) < 0.001) {
            return String.format("%.0f", score);
        }
        return String.format("%.1f", score);
    }

    /**
     * Formats currency values for display.
     */
    public static String formatCurrency(Number value) {
        if (value == null) return "N/A";
        return CURRENCY_FORMAT.format(value.doubleValue());
    }

    /**
     * Formats percentage values for display.
     */
    public static String formatPercentage(Double value) {
        if (value == null) return "N/A";
        return String.format("%.2f%%", value * 100);
    }
}
