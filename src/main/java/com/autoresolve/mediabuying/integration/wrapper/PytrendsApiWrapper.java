package com.autoresolve.mediabuying.integration.wrapper;

import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.integration.ratelimit.AdPlatformRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Wrapper for Google Trends (PyTrends) API.
 * <p>
 * PyTrends is a Python library with no formal Java API and requires
 * <strong>no API key</strong> — it scrapes publicly available Google Trends
 * data. This wrapper is mock-only for MVP. Future implementations may call
 * the Trends Explore endpoint directly or embed a Python subprocess.
 * </p>
 * API reference: https://github.com/GeneralMills/pytrends
 */
@Component
public class PytrendsApiWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(PytrendsApiWrapper.class);

    /**
     * Always {@code false} — PyTrends has no live Java API available,
     * so this wrapper always returns mock data.
     */
    private final boolean liveMode = false;

    public PytrendsApiWrapper(
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        System.out.println("=== PHASE: PytrendsApiWrapper constructor at " + System.currentTimeMillis() + " ===");
    }

    @Override
    public String getSourceName() {
        return "pytrends";
    }

    @Override
    public boolean isLive() {
        // PyTrends has no live Java API available
        return false;
    }

    /**
     * Fetches Google Trends search interest data for the given keyword and region.
     * <p>
     * No live Java API exists for PyTrends, so mock data is always returned.
     *
     * @param keyword the search term
     * @param region  ISO 3166-1 alpha-2 region code, e.g. "US"
     * @return RawSourceData with mock (or future real) trends data
     */
    public RawSourceData fetchTrends(String keyword, String region) {
        log.debug("Returning mock PyTrends data for keyword={}, region={}", keyword, region);
        return buildMockResponse(keyword, region);
    }

    private RawSourceData buildMockResponse(String keyword, String region) {
        String json = "{"
                + "\"keyword\":\"" + escapeJson(keyword) + "\","
                + "\"region\":\"" + escapeJson(region) + "\","
                + "\"interest_over_time\":[80,95,72,88,91,67],"
                + "\"related_queries\":[\"" + escapeJson(keyword) + " deals\",\"best " + escapeJson(keyword) + "\",\"" + escapeJson(keyword) + " near me\",\"" + escapeJson(keyword) + " reviews\"],"
                + "\"regional_breakdown\":{\"" + escapeJson(region) + "\":100,\"GB\":45,\"CA\":38,\"AU\":22},"
                + "\"avg_monthly_volume\":" + MockDataHelper.randomInt(10000, 500000)
                + "}";

        return RawSourceData.builder()
                .sourceName("pytrends")
                .sourceUrl("https://trends.google.com/trends/explore?q=" + keyword)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("Trends data for keyword '" + keyword + "' in " + region
                        + ": avg monthly volume " + MockDataHelper.randomInt(10000, 500000))
                .recordCount(6)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("pytrends"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
