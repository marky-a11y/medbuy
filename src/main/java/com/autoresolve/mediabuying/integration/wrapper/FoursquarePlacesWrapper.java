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
 * Wrapper for Foursquare Places API (v3).
 * <p>
 * API reference: https://docs.foursquare.com/developer/reference/places-api
 * Auth: API key (Bearer token in Authorization header).
 * Endpoint: GET /v3/places/search
 * </p>
 */
@Component
public class FoursquarePlacesWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(FoursquarePlacesWrapper.class);

    private final String apiKey;
    private final boolean liveMode;

    public FoursquarePlacesWrapper(
            @Value("${integration.foursquare-places.api-key:}") String apiKey,
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
        return "foursquare_places";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches venue data for the given location and category.
     *
     * @param near     location description, e.g. "New York, NY"
     * @param category venue category, e.g. "retail", "dining", "fitness"
     * @return RawSourceData with venue data
     */
    public RawSourceData fetchVenues(String near, String category) {
        if (!liveMode) {
            log.debug("Returning mock Foursquare Places data for near={}, category={}", near, category);
            return buildMockResponse(near, category);
        }
        try {
            // 1. Build URL with query parameters
            String url = "https://api.foursquare.com/v3/places/search?near="
                    + URLEncoder.encode(near, "UTF-8")
                    + "&limit=20";

            // 2. Set Authorization header (Foursquare uses the API key directly in the header)
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // 5. Extract data from response
            JsonNode results = root.get("results");
            int recordCount = (results != null && results.isArray()) ? results.size() : 0;

            // Build a meaningful summary
            String summary = "Real Foursquare data for '" + category + "' near " + near
                    + ": " + recordCount + " venues found";
            if (results != null && results.isArray() && results.size() > 0) {
                String firstName = results.get(0).get("name").asText();
                summary = "Real Foursquare data: " + firstName + " and " + (recordCount - 1)
                        + " others near " + near;
            }

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
            return buildMockResponse(near, category);
        }
    }

    private RawSourceData buildMockResponse(String near, String category) {
        int venueCount = MockDataHelper.randomInt(5, 15);
        double avgPopularity = MockDataHelper.randomInt(30, 95);
        int totalCheckIns = MockDataHelper.randomInt(500, 50000);

        String json = "{"
                + "\"near\":\"" + escapeJson(near) + "\","
                + "\"category\":\"" + escapeJson(category) + "\","
                + "\"total_venues\":" + venueCount + ","
                + "\"avg_popularity\":" + avgPopularity + ","
                + "\"total_check_ins\":" + totalCheckIns + ","
                + "\"categories\":[\"" + escapeJson(category) + "\",\"shopping\",\"food\"],"
                + "\"top_venues\":["
                + "{\"name\":\"" + escapeJson(category) + " Hub\",\"popularity\":" + (avgPopularity + 5)
                + ",\"check_ins\":" + (totalCheckIns / 3) + "},"
                + "{\"name\":\"" + escapeJson(category) + " Center\",\"popularity\":" + (avgPopularity - 3)
                + ",\"check_ins\":" + (totalCheckIns / 5) + "}"
                + "]}";

        return RawSourceData.builder()
                .sourceName("foursquare_places")
                .sourceUrl("https://api.foursquare.com/v3/places/search?near=" + near + "&category=" + category)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("Foursquare venues near '" + near + "' for category '" + category
                        + "': " + venueCount + " venues, avg popularity " + avgPopularity)
                .recordCount(venueCount)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("foursquare_places"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
