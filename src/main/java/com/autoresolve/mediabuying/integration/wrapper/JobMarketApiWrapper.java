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
 * Wrapper for the Job Market API (Adzuna as primary provider).
 * <p>
 * API reference: https://developer.adzuna.com/
 * Auth: API key + App ID (query parameters or Basic Auth).
 * Endpoint: GET /api/v1/en/{country}/search/{category}
 * </p>
 */
@Component
public class JobMarketApiWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(JobMarketApiWrapper.class);

    private final String apiKey;
    private final String appId;
    private final String provider;
    private final boolean liveMode;

    public JobMarketApiWrapper(
            @Value("${integration.job-market.api-key:}") String apiKey,
            @Value("${integration.job-market.app-id:}") String appId,
            @Value("${integration.job-market.provider:adzuna}") String provider,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.appId = appId;
        this.provider = provider;
        // Both apiKey and appId are required for live mode (Adzuna auth)
        this.liveMode = apiKey != null && !apiKey.isEmpty()
                && appId != null && !appId.isEmpty();
    }

    @Override
    public String getSourceName() {
        return "job_market";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches job listing data for the given keyword and location.
     *
     * @param keyword  job title or skill keyword, e.g. "software engineer"
     * @param location location string, e.g. "New York"
     * @return RawSourceData with job listing data
     */
    public RawSourceData fetchJobListings(String keyword, String location) {
        if (!liveMode) {
            log.debug("Returning mock Job Market data for keyword={}, location={}", keyword, location);
            return buildMockResponse(keyword, location);
        }
        try {
            // 1. Build URL — Adzuna Jobs API
            String url = "https://api.adzuna.com/v1/api/jobs/us/search/1"
                    + "?app_id=" + URLEncoder.encode(appId, "UTF-8")
                    + "&app_key=" + URLEncoder.encode(apiKey, "UTF-8")
                    + "&what=" + URLEncoder.encode(keyword, "UTF-8")
                    + "&where=" + URLEncoder.encode(location, "UTF-8")
                    + "&results_per_page=20";

            // 2. No special auth headers — app_id and app_key are query params
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // 5. Extract data from response
            int jobCount = root.has("count") ? root.get("count").asInt() : 0;
            JsonNode results = root.get("results");
            int recordCount = (results != null && results.isArray()) ? results.size() : 0;
            // Use the total count from the API if available, otherwise fall back to array size
            int totalCount = jobCount > 0 ? jobCount : recordCount;

            // Extract first job title for summary
            String firstTitle = "";
            if (results != null && results.isArray() && results.size() > 0) {
                JsonNode firstJob = results.get(0);
                if (firstJob.has("title")) {
                    firstTitle = firstJob.get("title").asText();
                }
            }

            String summary = "Real Adzuna data: " + totalCount + " jobs for '" + keyword + "' in " + location;
            if (!firstTitle.isEmpty()) {
                summary = "Real Adzuna data: \"" + firstTitle + "\" and " + (totalCount - 1)
                        + " others for '" + keyword + "' in " + location;
            }

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(UrlSanitizer.sanitize(url))
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(totalCount)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PROPRIETARY")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed: {} — falling back to mock", getSourceName(), e.getMessage());
            return buildMockResponse(keyword, location);
        }
    }

    private RawSourceData buildMockResponse(String keyword, String location) {
        int jobCount = MockDataHelper.randomInt(15, 200);
        double avgSalary = MockDataHelper.randomPrice() * 1000;
        String[] categories = {"technology", "finance", "healthcare", "marketing", "retail"};
        String category = categories[MockDataHelper.randomInt(0, categories.length - 1)];

        String json = "{"
                + "\"keyword\":\"" + escapeJson(keyword) + "\","
                + "\"location\":\"" + escapeJson(location) + "\","
                + "\"provider\":\"" + escapeJson(provider) + "\","
                + "\"total_jobs\":" + jobCount + ","
                + "\"avg_salary\":" + avgSalary + ","
                + "\"category\":\"" + category + "\","
                + "\"top_companies\":[\"TechCorp\",\"FinanceInc\",\"HealthSys\",\"MarketPro\"],"
                + "\"salary_range\":{\"min\":" + (avgSalary * 0.6)
                + ",\"max\":" + (avgSalary * 1.4) + "},"
                + "\"demand_trend\":\"" + (MockDataHelper.randomInt(0, 1) == 0 ? "growing" : "stable") + "\""
                + "}";

        return RawSourceData.builder()
                .sourceName("job_market")
                .sourceUrl("https://api.adzuna.com/v1/api/jobs/us/search/1?app_id="
                        + appId + "&app_key=" + (apiKey != null ? "***" : ""))
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("Job market data for '" + keyword + "' in " + location
                        + ": " + jobCount + " jobs, avg salary $" + String.format("%.0f", avgSalary)
                        + ", category " + category)
                .recordCount(jobCount)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("job_market"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
