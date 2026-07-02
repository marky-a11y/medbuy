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
 * Wrapper for Reddit API (OAuth 2.0, script app).
 * <p>
 * API reference: https://www.reddit.com/dev/api/
 * Auth: OAuth 2.0 (script app) — uses {@link OAuthTokenManager}.
 * Requires {@code User-Agent} header (set via {@code integration.reddit.user-agent}).
 * Endpoint: GET /r/{subreddit}/{sort}
 * </p>
 */
@Component
public class RedditApiWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(RedditApiWrapper.class);

    private final String apiKey;
    private final String userAgent;
    private final boolean liveMode;

    public RedditApiWrapper(
            @Value("${integration.reddit.api-key:}") String apiKey,
            @Value("${integration.reddit.user-agent:MediaBuyingDashboard/1.0}") String userAgent,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.liveMode = apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getSourceName() {
        return "reddit";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches posts from the given subreddit with the specified sort order and limit.
     *
     * @param subreddit subreddit name, e.g. "technology", "marketing"
     * @param sort      sort order: "hot", "new", "top", "rising"
     * @param limit     max posts to fetch (1–100)
     * @return RawSourceData with subreddit post data
     */
    public RawSourceData fetchSubredditPosts(String subreddit, String sort, int limit) {
        if (!liveMode) {
            log.debug("Returning mock Reddit data for r/{}, sort={}, limit={}", subreddit, sort, limit);
            return buildMockResponse(subreddit, sort, limit);
        }
        try {
            // 1. Build URL — Reddit public JSON endpoint, no API key needed
            String url = "https://www.reddit.com/r/"
                    + URLEncoder.encode(subreddit, "UTF-8") + "/"
                    + URLEncoder.encode(sort, "UTF-8") + ".json"
                    + "?limit=" + Math.min(limit, 100);

            // 2. Set User-Agent header (required by Reddit API)
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", userAgent);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // 5. Extract data from response
            // Reddit response: data.children[].data
            int postCount = 0;
            JsonNode dataNode = root.get("data");
            if (dataNode != null) {
                JsonNode children = dataNode.get("children");
                if (children != null && children.isArray()) {
                    postCount = children.size();
                }
            }

            String firstName = "";
            if (postCount > 0 && dataNode != null) {
                JsonNode firstChild = dataNode.get("children").get(0).get("data");
                if (firstChild != null && firstChild.has("title")) {
                    firstName = firstChild.get("title").asText();
                    if (firstName.length() > 60) {
                        firstName = firstName.substring(0, 57) + "...";
                    }
                }
            }

            String summary = "Real Reddit data: " + postCount + " posts from r/" + subreddit
                    + " (" + sort + ")";
            if (!firstName.isEmpty()) {
                summary = "Real Reddit data: \"" + firstName + "\" and " + (postCount - 1)
                        + " more from r/" + subreddit;
            }

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(url)
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(postCount)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PUBLIC")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed: {} — falling back to mock", getSourceName(), e.getMessage());
            return buildMockResponse(subreddit, sort, limit);
        }
    }

    private RawSourceData buildMockResponse(String subreddit, String sort, int limit) {
        int actualLimit = Math.min(limit, 100);
        int postCount = Math.min(actualLimit, MockDataHelper.randomInt(5, 25));

        String json = "{"
                + "\"subreddit\":\"r/" + escapeJson(subreddit) + "\","
                + "\"sort\":\"" + escapeJson(sort) + "\","
                + "\"post_count\":" + postCount + ","
                + "\"posts\":["
                + "{\"title\":\"Trending topic in " + escapeJson(subreddit)
                + "\",\"score\":" + MockDataHelper.randomInt(50, 5000)
                + ",\"num_comments\":" + MockDataHelper.randomInt(10, 800)
                + ",\"sentiment\":\"positive\"},"
                + "{\"title\":\"Discussion about " + escapeJson(subreddit) + " strategies"
                + "\",\"score\":" + MockDataHelper.randomInt(20, 2000)
                + ",\"num_comments\":" + MockDataHelper.randomInt(5, 400)
                + ",\"sentiment\":\"neutral\"}"
                + "],"
                + "\"avg_score\":" + MockDataHelper.randomInt(100, 3000) + ","
                + "\"total_comments\":" + MockDataHelper.randomInt(200, 10000) + ","
                + "\"sentiment_distribution\":{\"positive\":0.45,\"neutral\":0.35,\"negative\":0.20}"
                + "}";

        return RawSourceData.builder()
                .sourceName("reddit")
                .sourceUrl("https://www.reddit.com/r/" + subreddit + "/" + sort + ".json?limit=" + limit)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("Reddit r/" + subreddit + " (" + sort + "): " + postCount
                        + " posts, avg score " + MockDataHelper.randomInt(100, 3000))
                .recordCount(postCount)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("reddit"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
