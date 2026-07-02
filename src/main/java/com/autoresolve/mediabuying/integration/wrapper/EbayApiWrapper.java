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
 * Wrapper for eBay Finding API.
 * <p>
 * API reference: https://developer.ebay.com/Devzone/finding/Concepts/FindingAPIGuide.html
 * Auth: OAuth 2.0 (App ID / Client Credentials via OAuthTokenManager).
 * Endpoint: GET /services/search/FindingService/v1
 * </p>
 */
@Component
public class EbayApiWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(EbayApiWrapper.class);

    private final String apiKey;
    private final String appId;
    private final boolean liveMode;

    public EbayApiWrapper(
            @Value("${integration.ebay.api-key:}") String apiKey,
            @Value("${integration.ebay.app-id:}") String appId,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.appId = appId;
        // Both apiKey and appId are required for live mode
        this.liveMode = apiKey != null && !apiKey.isEmpty()
                && appId != null && !appId.isEmpty();
    }

    @Override
    public String getSourceName() {
        return "ebay";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches eBay item listings matching the given keyword and category.
     *
     * @param keyword    search keyword, e.g. "iPhone 15"
     * @param categoryId eBay category ID, e.g. "9355" for Cell Phones
     * @return RawSourceData with item listing data
     */
    public RawSourceData fetchListings(String keyword, String categoryId) {
        if (!liveMode) {
            log.debug("Returning mock eBay data for keyword={}, category={}", keyword, categoryId);
            return buildMockResponse(keyword, categoryId);
        }
        try {
            // 1. Build URL with query parameters
            // eBay Finding API uses SECURITY-APPNAME as query parameter for auth
            String url = "https://svcs.ebay.com/services/search/FindingService/v1"
                    + "?OPERATION-NAME=findItemsByKeywords"
                    + "&keywords=" + URLEncoder.encode(keyword, "UTF-8")
                    + "&SECURITY-APPNAME=" + URLEncoder.encode(apiKey != null ? apiKey : appId, "UTF-8")
                    + "&RESPONSE-DATA-FORMAT=JSON"
                    + "&paginationInput.entriesPerPage=20";

            // 2. No special auth headers needed — App ID is in the query string
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
            // eBay response structure: findItemsByKeywordsResponse[0].searchResult[0].@count + .item[]
            int itemCount = 0;
            JsonNode findResponse = root.get("findItemsByKeywordsResponse");
            if (findResponse != null && findResponse.isArray() && findResponse.size() > 0) {
                JsonNode searchResult = findResponse.get(0).get("searchResult");
                if (searchResult != null && searchResult.isArray() && searchResult.size() > 0) {
                    JsonNode countAttr = searchResult.get(0).get("@count");
                    if (countAttr != null) {
                        itemCount = countAttr.asInt();
                    }
                    // Fall back to actual item array size
                    JsonNode items = searchResult.get(0).get("item");
                    if (items != null && items.isArray() && itemCount == 0) {
                        itemCount = items.size();
                    }
                }
            }

            String summary = "Real eBay data for '" + keyword + "' (cat " + categoryId
                    + "): " + itemCount + " items found";

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(UrlSanitizer.sanitize(url))
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(itemCount)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PROPRIETARY")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed: {} — falling back to mock", getSourceName(), e.getMessage());
            return buildMockResponse(keyword, categoryId);
        }
    }

    private RawSourceData buildMockResponse(String keyword, String categoryId) {
        int itemCount = MockDataHelper.randomInt(10, 50);
        double avgPrice = MockDataHelper.randomPrice();
        int totalSold = MockDataHelper.randomInt(20, 500);

        String json = "{"
                + "\"keyword\":\"" + escapeJson(keyword) + "\","
                + "\"category_id\":\"" + escapeJson(categoryId) + "\","
                + "\"total_items\":" + itemCount + ","
                + "\"avg_price\":" + avgPrice + ","
                + "\"total_sold\":" + totalSold + ","
                + "\"conditions\":{\"new\":" + (itemCount / 2)
                + ",\"used\":" + (itemCount / 3)
                + ",\"refurbished\":" + (itemCount / 6) + "},"
                + "\"listings\":["
                + "{\"title\":\"" + escapeJson(keyword) + " - Premium\"," +
                "\"price\":" + (avgPrice * 1.3) + ",\"sold_count\":" + (totalSold / 4) + "},"
                + "{\"title\":\"" + escapeJson(keyword) + " - Standard\"," +
                "\"price\":" + avgPrice + ",\"sold_count\":" + (totalSold / 3) + "}"
                + "]}";

        return RawSourceData.builder()
                .sourceName("ebay")
                .sourceUrl("https://svcs.ebay.com/services/search/FindingService/v1?OPERATION-NAME=findItemsByKeywords&keywords=" + keyword)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("eBay listings for '" + keyword + "' (cat " + categoryId
                        + "): " + itemCount + " items, avg $" + String.format("%.2f", avgPrice)
                        + ", " + totalSold + " sold")
                .recordCount(itemCount)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("ebay"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
