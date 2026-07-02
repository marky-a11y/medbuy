package com.autoresolve.mediabuying.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Utility for sanitizing URLs that may contain sensitive query parameters
 * (API keys, tokens, etc.) before persisting or logging.
 */
public final class UrlSanitizer {

    private UrlSanitizer() {
    }

    /** Query parameter names that carry API keys or secrets. */
    private static final Pattern SENSITIVE_PARAM_PATTERN =
            Pattern.compile(
                    "(&|\\?)(key|api[Kk]ey|api_key|access_token|SECURITY-APPNAME|app_id|app_key|token|secret)"
                            + "=[^&]+",
                    Pattern.CASE_INSENSITIVE
            );

    /** Query parameter values to redact (for display-friendly sanitization). */
    private static final Pattern SENSITIVE_VALUE_PATTERN =
            Pattern.compile(
                    "((&|\\?)(key|api[Kk]ey|api_key|access_token|SECURITY-APPNAME|app_id|app_key|token|secret)=)[^&]+",
                    Pattern.CASE_INSENSITIVE
            );

    /**
     * Removes sensitive query parameters from a URL.
     * <p>
     * Example: {@code https://api.census.gov/data?...&key=MY_KEY}
     * becomes   {@code https://api.census.gov/data?...&key=REDACTED}
     * </p>
     *
     * @param url the original URL that may contain API keys
     * @return a sanitized URL with key values replaced by {@code REDACTED},
     *         or the original URL if it cannot be parsed
     */
    public static String sanitize(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        try {
            // Replace the value portion of known sensitive parameters with "REDACTED"
            String result = SENSITIVE_VALUE_PATTERN.matcher(url).replaceAll("$1REDACTED");
            return result;
        } catch (Exception e) {
            // If something goes wrong, return the original URL rather than failing
            return url;
        }
    }

    /**
     * Returns {@code true} if the given URL contains any sensitive query parameters.
     */
    public static boolean containsSensitiveParams(String url) {
        if (url == null) {
            return false;
        }
        return SENSITIVE_PARAM_PATTERN.matcher(url).find();
    }
}
