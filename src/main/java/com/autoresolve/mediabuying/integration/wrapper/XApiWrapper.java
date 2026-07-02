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
 * Wrapper for X (Twitter) API v2.
 * <p>
 * API reference: https://developer.twitter.com/en/docs/twitter-api
 * Auth: OAuth 2.0 Bearer Token (simpler than OAuth 1.0a).
 * Endpoint: GET /2/tweets/search/recent
 * </p>
 */
@Component
public class XApiWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(XApiWrapper.class);

    private final String apiKey;
    private final String bearerToken;
    private final boolean liveMode;

    public XApiWrapper(
            @Value("${integration.x-api.api-key:}") String apiKey,
            @Value("${integration.x-api.bearer-token:}") String bearerToken,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.bearerToken = bearerToken;
        // Either apiKey or bearerToken is sufficient for live mode
        this.liveMode = (apiKey != null && !apiKey.isEmpty())
                || (bearerToken != null && !bearerToken.isEmpty());
    }

    @Override
    public String getSourceName() {
        return "x_api";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches recent tweets matching the given query.
     *
     * @param query       search query, e.g. "digital marketing -is:retweet"
     * @param maxResults  max tweets to return (10–100)
     * @return RawSourceData with tweet data
     */
    public RawSourceData fetchRecentTweets(String query, int maxResults) {
        if (!liveMode) {
            log.debug("Returning mock X API data for query={}, maxResults={}", query, maxResults);
            return buildMockResponse(query, maxResults);
        }
        try {
            // 1. Build URL — Twitter API v2 recent search endpoint
            String url = "https://api.twitter.com/2/tweets/search/recent?query="
                    + URLEncoder.encode(query, "UTF-8")
                    + "&max_results=" + Math.min(Math.max(maxResults, 10), 100);

            // 2. Set Bearer token authorization header
            String token = (bearerToken != null && !bearerToken.isEmpty())
                    ? bearerToken : apiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // 5. Extract data from response
            int resultCount = 0;
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                resultCount = data.size();
            }
            // Also check meta.result_count if available
            if (root.has("meta") && root.get("meta").has("result_count")) {
                resultCount = root.get("meta").get("result_count").asInt();
            }

            // Extract first tweet text for summary
            String firstText = "";
            if (data != null && data.isArray() && data.size() > 0) {
                JsonNode firstTweet = data.get(0);
                if (firstTweet.has("text")) {
                    firstText = firstTweet.get("text").asText();
                    if (firstText.length() > 60) {
                        firstText = firstText.substring(0, 57) + "...";
                    }
                }
            }

            String summary = "Real X/Twitter data: " + resultCount + " tweets for '" + query + "'";
            if (!firstText.isEmpty()) {
                summary = "Real X/Twitter data: \"" + firstText + "\" and " + (resultCount - 1)
                        + " more for '" + query + "'";
            }

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(url)
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(resultCount)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PROPRIETARY")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed: {} — falling back to mock", getSourceName(), e.getMessage());
            return buildMockResponse(query, maxResults);
        }
    }

    private RawSourceData buildMockResponse(String query, int maxResults) {
        int actualResults = Math.min(maxResults, MockDataHelper.randomInt(10, 30));

        String json = "{"
                + "\"query\":\"" + escapeJson(query) + "\","
                + "\"result_count\":" + actualResults + ","
                + "\"tweets\":["
                + "{\"id\":\"1234567890\",\"text\":\"Excited about "
                + escapeJson(query) + " trends this quarter!\","
                + "\"author\":\"@marketingpro\",\"retweet_count\":" + MockDataHelper.randomInt(10, 500)
                + ",\"like_count\":" + MockDataHelper.randomInt(20, 2000)
                + ",\"hashtags\":[\"#" + escapeJson(query.replaceAll("\\s+", ""))
                + "\",\"#digital\"]},"
                + "{\"id\":\"1234567891\",\"text\":\"New study on "
                + escapeJson(query) + " shows 40% growth\","
                + "\"author\":\"@businessdaily\",\"retweet_count\":" + MockDataHelper.randomInt(5, 300)
                + ",\"like_count\":" + MockDataHelper.randomInt(10, 1000)
                + ",\"hashtags\":[\"#" + escapeJson(query.replaceAll("\\s+", "")) + "\",\"#growth\"]}"
                + "],"
                + "\"avg_likes\":" + MockDataHelper.randomInt(50, 1500) + ","
                + "\"avg_retweets\":" + MockDataHelper.randomInt(10, 400) + ","
                + "\"sentiment_score\":" + (Math.round(MockDataHelper.randomInt(-50, 80) * 10.0) / 1000.0)
                + "}";

        return RawSourceData.builder()
                .sourceName("x_api")
                .sourceUrl("https://api.twitter.com/2/tweets/search/recent?query=" + query + "&max_results=" + maxResults)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("X/Twitter recent tweets for '" + query + "': "
                        + actualResults + " results")
                .recordCount(actualResults)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("x_api"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
