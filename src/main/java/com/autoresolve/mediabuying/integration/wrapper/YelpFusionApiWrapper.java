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
 * Wrapper for Yelp Fusion API.
 * <p>
 * API reference: https://fusion.yelp.com/
 * Auth: API key (Bearer token in Authorization header).
 * Endpoint: GET /v3/businesses/search
 * </p>
 */
@Component
public class YelpFusionApiWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(YelpFusionApiWrapper.class);

    private final String apiKey;
    private final boolean liveMode;

    public YelpFusionApiWrapper(
            @Value("${integration.yelp-fusion.api-key:}") String apiKey,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.liveMode = apiKey != null && !apiKey.isEmpty();
        System.out.println("=== PHASE: YelpFusionApiWrapper constructor at " + System.currentTimeMillis() + " ===");
    }

    @Override
    public String getSourceName() {
        return "yelp_fusion";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches Yelp business data for the given search term and location.
     *
     * @param searchTerm e.g. "restaurants", "retail", "health"
     * @param location   e.g. "San Francisco, CA"
     * @return RawSourceData with business listing data
     */
    public RawSourceData fetchBusinesses(String searchTerm, String location) {
        if (!liveMode) {
            log.debug("Returning mock Yelp Fusion data for term={}, location={}", searchTerm, location);
            return buildMockResponse(searchTerm, location);
        }
        try {
            // 1. Build URL with query parameters
            String url = "https://api.yelp.com/v3/businesses/search?term="
                    + URLEncoder.encode(searchTerm, "UTF-8")
                    + "&location=" + URLEncoder.encode(location, "UTF-8")
                    + "&limit=20";

            // 2. Set Authorization header with Bearer token
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // 5. Extract data from response
            JsonNode businesses = root.get("businesses");
            int recordCount = (businesses != null && businesses.isArray()) ? businesses.size() : 0;
            int total = root.has("total") ? root.get("total").asInt() : recordCount;

            // Build a meaningful summary from the first business
            String summary = "Real Yelp data for '" + searchTerm + "' in " + location
                    + ": " + total + " businesses found";
            if (businesses != null && businesses.isArray() && businesses.size() > 0) {
                String firstName = businesses.get(0).get("name").asText();
                summary = "Real Yelp data: " + firstName + " and " + (total - 1) + " others for '"
                        + searchTerm + "' in " + location;
            }

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(url)
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(total)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PROPRIETARY")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed: {} — falling back to mock", getSourceName(), e.getMessage());
            return buildMockResponse(searchTerm, location);
        }
    }

    private RawSourceData buildMockResponse(String searchTerm, String location) {
        int bizCount = MockDataHelper.randomInt(3, 8);
        double avgRating = Math.round(MockDataHelper.randomInt(30, 50) / 10.0 * 10.0) / 10.0;
        int totalReviews = MockDataHelper.randomInt(50, 2000);

        String json = "{"
                + "\"search_term\":\"" + escapeJson(searchTerm) + "\","
                + "\"location\":\"" + escapeJson(location) + "\","
                + "\"total_businesses\":" + bizCount + ","
                + "\"average_rating\":" + avgRating + ","
                + "\"total_reviews\":" + totalReviews + ","
                + "\"categories\":[\"" + escapeJson(searchTerm) + "\",\"shopping\",\"services\"],"
                + "\"businesses\":["
                + "{\"name\":\"" + escapeJson(searchTerm) + " Spot 1\",\"rating\":" + (avgRating + 0.2)
                + ",\"review_count\":" + (totalReviews / bizCount) + "},"
                + "{\"name\":\"" + escapeJson(searchTerm) + " Place 2\",\"rating\":" + (avgRating - 0.1)
                + ",\"review_count\":" + (totalReviews / bizCount / 2) + "}"
                + "]}";

        return RawSourceData.builder()
                .sourceName("yelp_fusion")
                .sourceUrl("https://api.yelp.com/v3/businesses/search?term=" + searchTerm + "&location=" + location)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("Yelp data for '" + searchTerm + "' in " + location
                        + ": " + bizCount + " businesses, avg rating " + avgRating)
                .recordCount(bizCount)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("yelp_fusion"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
