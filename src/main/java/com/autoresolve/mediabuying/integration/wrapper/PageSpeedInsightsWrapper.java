package com.autoresolve.mediabuying.integration.wrapper;

import com.autoresolve.mediabuying.util.UrlSanitizer;

import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.integration.ratelimit.AdPlatformRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.time.Instant;

/**
 * Wrapper for Google PageSpeed Insights API.
 * <p>
 * API reference: https://developers.google.com/speed/docs/insights/v5/get-started
 * Auth: API key (query parameter {@code key}).
 * Endpoint: GET /pagespeedonline/v5/runPagespeed
 * </p>
 */
@Component
public class PageSpeedInsightsWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(PageSpeedInsightsWrapper.class);

    private final String apiKey;
    private final boolean liveMode;

    public PageSpeedInsightsWrapper(
            @Value("${integration.pagespeed-insights.api-key:}") String apiKey,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.liveMode = apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getSourceName() {
        return "pagespeed_insights";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches PageSpeed Insights data for the given URL and strategy.
     *
     * @param url      the website URL to analyze
     * @param strategy "mobile" or "desktop"; defaults to "mobile" if null
     * @return RawSourceData with performance, SEO, and accessibility scores
     */
    public RawSourceData fetchPageSpeed(String url, String strategy) {
        if (!liveMode) {
            log.debug("Returning mock PageSpeed Insights data for url={}, strategy={}", url, strategy);
            return buildMockResponse(url, strategy);
        }
        try {
            // 1. Build URL with query parameters
            String requestUrl = "https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url="
                    + URLEncoder.encode(url, "UTF-8")
                    + "&strategy=" + (strategy != null ? URLEncoder.encode(strategy, "UTF-8") : "mobile")
                    + "&key=" + URLEncoder.encode(apiKey, "UTF-8");

            // 2. Set headers
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // 5. Extract lighthouse category scores (0.0–1.0, convert to 0–100 scale)
            JsonNode categories = root.path("lighthouseResult").path("categories");
            int perfScore = (int) Math.round(categories.path("performance").path("score").asDouble() * 100.0);
            int seoScore = (int) Math.round(categories.path("seo").path("score").asDouble() * 100.0);
            int accessibilityScore = (int) Math.round(categories.path("accessibility").path("score").asDouble() * 100.0);

            String summary = "PageSpeed: performance=" + perfScore + ", SEO=" + seoScore
                    + ", accessibility=" + accessibilityScore + " for " + url;

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(UrlSanitizer.sanitize(requestUrl))
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(3)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PUBLIC")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed for URL '{}': {} — falling back to mock",
                    getSourceName(), url, e.getMessage());
            return buildMockResponse(url, strategy);
        }
    }

    private RawSourceData buildMockResponse(String url, String strategy) {
        String effectiveStrategy = strategy != null ? strategy : "mobile";
        String json = "{"
                + "\"url\":\"" + escapeJson(url) + "\","
                + "\"strategy\":\"" + escapeJson(effectiveStrategy) + "\","
                + "\"lighthouseResult\":{"
                + "\"categories\":{"
                + "\"performance\":{\"score\":0.72},"
                + "\"seo\":{\"score\":0.88},"
                + "\"accessibility\":{\"score\":0.65}"
                + "}}}";

        return RawSourceData.builder()
                .sourceName("pagespeed_insights")
                .sourceUrl("https://pagespeed.web.dev/report?url=" + url)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("PageSpeed mock: perf=72, SEO=88, accessibility=65 for " + url)
                .recordCount(3)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("pagespeed_insights"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
