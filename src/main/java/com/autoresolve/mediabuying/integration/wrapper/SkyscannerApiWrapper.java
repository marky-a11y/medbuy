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
 * Wrapper for Skyscanner Partner API (Browse Quotes).
 * <p>
 * API reference: https://www.partners.skyscanner.net/
 * Auth: API key (Bearer token).
 * Endpoint: Browse Quotes API (GET /apiservices/browsequotes/v1.0/...)
 * </p>
 */
@Component
public class SkyscannerApiWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(SkyscannerApiWrapper.class);

    private final String apiKey;
    private final boolean liveMode;

    public SkyscannerApiWrapper(
            @Value("${integration.skyscanner.api-key:}") String apiKey,
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
        return "skyscanner";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches flight price data for the given route and date.
     *
     * @param origin      IATA origin airport code, e.g. "JFK"
     * @param destination IATA destination airport code, e.g. "LAX"
     * @param date        departure date, e.g. "2026-08-15"
     * @return RawSourceData with flight pricing data
     */
    public RawSourceData fetchFlightPrices(String origin, String destination, String date) {
        if (!liveMode) {
            log.debug("Returning mock Skyscanner data for {}->{} on {}", origin, destination, date);
            return buildMockResponse(origin, destination, date);
        }
        try {
            // 1. Build URL for Skyscanner Browse Quotes API
            // The Skyscanner Partner API uses a country/currency/locale prefix
            String url = "https://partners.skyscanner.net/apiservices/browsequotes/v1.0/US/USD/en-US/"
                    + URLEncoder.encode(origin, "UTF-8") + "/"
                    + URLEncoder.encode(destination, "UTF-8") + "/"
                    + URLEncoder.encode(date, "UTF-8")
                    + "?apiKey=" + URLEncoder.encode(apiKey, "UTF-8");

            // 2. Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // 5. Extract data from response
            int quoteCount = 0;
            JsonNode quotes = root.get("Quotes");
            if (quotes != null && quotes.isArray()) {
                quoteCount = quotes.size();
            }

            // Extract min/max price from the first two quotes if available
            double minPrice = 0;
            double maxPrice = 0;
            if (quotes != null && quotes.isArray() && quotes.size() > 0) {
                minPrice = quotes.get(0).get("MinPrice").asDouble();
                maxPrice = minPrice;
                for (int i = 1; i < quotes.size(); i++) {
                    double p = quotes.get(i).get("MinPrice").asDouble();
                    if (p < minPrice) minPrice = p;
                    if (p > maxPrice) maxPrice = p;
                }
            }

            String summary = "Real Skyscanner data for " + origin + " → " + destination + " on " + date
                    + ": " + quoteCount + " quotes"
                    + (minPrice > 0 ? ", $" + (int) minPrice + "–$" + (int) maxPrice : "");

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(UrlSanitizer.sanitize(url))
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount(quoteCount)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PROPRIETARY")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed: {} — falling back to mock", getSourceName(), e.getMessage());
            return buildMockResponse(origin, destination, date);
        }
    }

    private RawSourceData buildMockResponse(String origin, String destination, String date) {
        int quoteCount = MockDataHelper.randomInt(5, 20);
        double minPrice = MockDataHelper.randomInt(150, 600);
        double maxPrice = MockDataHelper.randomInt(600, 2000);
        String[] carriers = {"Delta", "United", "American", "Southwest", "JetBlue", "Alaska"};
        String json = "{"
                + "\"origin\":\"" + escapeJson(origin) + "\","
                + "\"destination\":\"" + escapeJson(destination) + "\","
                + "\"departure_date\":\"" + escapeJson(date) + "\","
                + "\"quote_count\":" + quoteCount + ","
                + "\"min_price\":" + minPrice + ","
                + "\"max_price\":" + maxPrice + ","
                + "\"avg_price\":" + ((minPrice + maxPrice) / 2.0) + ","
                + "\"carriers\":[\"" + carriers[MockDataHelper.randomInt(0, carriers.length - 1)]
                + "\",\"" + carriers[MockDataHelper.randomInt(0, carriers.length - 1)] + "\"],"
                + "\"demand_index\":" + MockDataHelper.randomInt(40, 100)
                + "}";

        return RawSourceData.builder()
                .sourceName("skyscanner")
                .sourceUrl("https://partners.skyscanner.net/apiservices/browsequotes/v1.0/US/USD/en-US/"
                        + origin + "/" + destination + "/" + date)
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary("Flight prices for " + origin + " → " + destination + " on " + date
                        + ": " + quoteCount + " quotes, $" + (int) minPrice + "–$" + (int) maxPrice)
                .recordCount(quoteCount)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("skyscanner"))
                .licenseType("PUBLIC")
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
