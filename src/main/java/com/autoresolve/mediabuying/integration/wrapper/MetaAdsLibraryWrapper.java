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
 * Wrapper for Meta (Facebook) Ad Library API.
 * <p>
 * API reference: https://www.facebook.com/ads/library/api/
 * Auth: Access token (simpler than full OAuth — use the Meta Ads Manager access token).
 * Endpoint: GET /v18.0/ads_archive
 * </p>
 */
@Component
public class MetaAdsLibraryWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(MetaAdsLibraryWrapper.class);

    private final String accessToken;
    private final boolean liveMode;

    public MetaAdsLibraryWrapper(
            @Value("${integration.meta-ads-library.access-token:}") String accessToken,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.accessToken = accessToken;
        this.liveMode = accessToken != null && !accessToken.isEmpty();
    }

    @Override
    public String getSourceName() {
        return "meta_ads_library";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches ad library data for the given search terms and country.
     *
     * @param searchTerms comma-separated search terms, e.g. "digital marketing, advertising"
     * @param country     ISO 3166-1 alpha-2 country code, e.g. "US"
     * @return RawSourceData with ad library data
     */
    public RawSourceData fetchAds(String searchTerms, String country) {
        if (!liveMode) {
            log.debug("Returning mock Meta Ads Library data for terms={}, country={}", searchTerms, country);
            return buildMockResponse(searchTerms, country);
        }
        try {
            // 1. Build URL — Meta Graph API ads_archive endpoint
            String url = "https://graph.facebook.com/v18.0/ads_archive"
                    + "?search_terms=" + URLEncoder.encode(searchTerms, "UTF-8")
                    + "&country=" + URLEncoder.encode(country, "UTF-8")
                    + "&access_token=" + URLEncoder.encode(accessToken, "UTF-8")
                    + "&limit=20";

            // 2. No special auth headers — access token is in the query string
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
            int adCount = 0;
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                adCount = data.size();
            }

            String summary = "Real Meta Ads Library data for '" + searchTerms + "' in " + country
                    + ": " + adCount + " ads found";

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(UrlSanitizer.sanitize(url))
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(adCount)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PROPRIETARY")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed: {} — falling back to mock", getSourceName(), e.getMessage());
            return buildMockResponse(searchTerms, country);
        }
    }

    private RawSourceData buildMockResponse(String searchTerms, String country) {
        int activeAdCount = MockDataHelper.randomInt(50, 5000);
        double minSpend = MockDataHelper.randomInt(1000, 50000);
        double maxSpend = MockDataHelper.randomInt(50000, 500000);
        String[] demographics = {"18-24", "25-34", "35-44", "45-54", "55+"};

        String json = "{"
                + "\"search_terms\":\"" + escapeJson(searchTerms) + "\","
                + "\"country\":\"" + escapeJson(country) + "\","
                + "\"active_ad_count\":" + activeAdCount + ","
                + "\"spend_range\":{\"min\":" + minSpend + ",\"max\":" + maxSpend + "},"
                + "\"primary_demographic\":\"" + demographics[MockDataHelper.randomInt(0, demographics.length - 1)] + "\","
                + "\"reach_estimate\":" + (activeAdCount * MockDataHelper.randomInt(100, 5000)) + ","
                + "\"top_advertisers\":[\"MajorBrand\",\"TechCompany\",\"LocalBusiness\"],"
                + "\"ad_categories\":[\"e-commerce\",\"lead_generation\",\"brand_awareness\"],"
                + "\"avg_spend_per_ad\":" + Math.round(((minSpend + maxSpend) / 2.0 / activeAdCount) * 100.0) / 100.0
                + "}";

        return RawSourceData.builder()
                .sourceName("meta_ads_library")
                .sourceUrl("https://graph.facebook.com/v18.0/ads_archive?search_terms=" + searchTerms + "&country=" + country)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("Meta Ads Library data for '" + searchTerms + "' in " + country
                        + ": " + activeAdCount + " active ads, spend range $"
                        + String.format("%.0f", minSpend) + "–$" + String.format("%.0f", maxSpend))
                .recordCount(activeAdCount)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("meta_ads_library"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
