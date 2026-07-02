package com.autoresolve.mediabuying.integration.wrapper;

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
 * Wrapper for Bing Webmaster Tools REST API.
 * <p>
 * API reference: https://learn.microsoft.com/bingwebmaster/
 * Auth: API key (in request header {@code api-key}).
 * Endpoint: GET /webmaster/api/Json/GetSearchTraffic
 * </p>
 */
@Component
public class BingWebmasterWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(BingWebmasterWrapper.class);

    private final String apiKey;
    private final String siteUrl;
    private final boolean liveMode;

    public BingWebmasterWrapper(
            @Value("${integration.bing-webmaster.api-key:}") String apiKey,
            @Value("${integration.bing-webmaster.site-url:}") String siteUrl,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.siteUrl = siteUrl;
        this.liveMode = apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getSourceName() {
        return "bing_webmaster";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches Bing search traffic data for the configured site URL.
     *
     * @param siteUrlOverride optional override; if empty, uses configured siteUrl
     * @return RawSourceData with search traffic data
     */
    public RawSourceData fetchSearchTraffic(String siteUrlOverride) {
        String effectiveSiteUrl = (siteUrlOverride != null && !siteUrlOverride.isEmpty())
                ? siteUrlOverride : this.siteUrl;

        if (!liveMode) {
            log.debug("Returning mock Bing Webmaster data for site={}", effectiveSiteUrl);
            return buildMockResponse(effectiveSiteUrl);
        }
        try {
            // 1. Build URL with query parameter
            String url = "https://ssl.bing.com/webmaster/api/Json/GetSearchTraffic?siteUrl="
                    + URLEncoder.encode(effectiveSiteUrl, "UTF-8");

            // 2. Set api-key header
            HttpHeaders headers = new HttpHeaders();
            headers.set("api-key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // 5. Extract data from response
            int totalClicks = root.has("total_clicks") ? root.get("total_clicks").asInt() : 0;
            int totalImpressions = root.has("total_impressions") ? root.get("total_impressions").asInt() : 0;
            // Determine record count from top queries if present
            int recordCount = 1;
            if (root.has("top_queries") && root.get("top_queries").isArray()) {
                recordCount = root.get("top_queries").size();
            }

            String summary = "Real Bing Webmaster data for " + effectiveSiteUrl
                    + ": " + totalClicks + " clicks, " + totalImpressions + " impressions";

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(url)
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(recordCount)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PROPRIETARY")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed: {} — falling back to mock", getSourceName(), e.getMessage());
            return buildMockResponse(effectiveSiteUrl);
        }
    }

    private RawSourceData buildMockResponse(String siteUrl) {
        int totalClicks = MockDataHelper.randomInt(500, 15000);
        int totalImpressions = totalClicks * MockDataHelper.randomInt(10, 40);
        double ctr = Math.round((double) totalClicks / totalImpressions * 10000.0) / 100.0;
        double avgPosition = Math.round(MockDataHelper.randomInt(2, 20) * 10.0) / 10.0;

        String json = "{"
                + "\"site_url\":\"" + escapeJson(siteUrl) + "\","
                + "\"total_clicks\":" + totalClicks + ","
                + "\"total_impressions\":" + totalImpressions + ","
                + "\"ctr\":" + ctr + ","
                + "\"avg_position\":" + avgPosition + ","
                + "\"top_queries\":["
                + "{\"query\":\"digital marketing\",\"clicks\":" + (totalClicks / 4)
                + ",\"impressions\":" + (totalImpressions / 4) + "},"
                + "{\"query\":\"online advertising\",\"clicks\":" + (totalClicks / 6)
                + ",\"impressions\":" + (totalImpressions / 6) + "},"
                + "{\"query\":\"social media management\",\"clicks\":" + (totalClicks / 8)
                + ",\"impressions\":" + (totalImpressions / 8) + "}"
                + "]}";

        return RawSourceData.builder()
                .sourceName("bing_webmaster")
                .sourceUrl("https://ssl.bing.com/webmaster/api/Json/GetSearchTraffic?siteUrl=" + siteUrl)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("Bing search traffic for " + siteUrl
                        + ": " + totalClicks + " clicks, " + totalImpressions
                        + " impressions, CTR " + ctr + "%, avg position " + avgPosition)
                .recordCount(3)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("bing_webmaster"))
                .licenseType("PROPRIETARY")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
