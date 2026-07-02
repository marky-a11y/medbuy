package com.autoresolve.mediabuying.integration.wrapper;

import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.dto.PlatformApiResponse;
import com.autoresolve.mediabuying.integration.ratelimit.AdPlatformRateLimiter;
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

import java.math.BigDecimal;
import java.util.Map;

/**
 * Wrapper for Google Ads API v16.
 * <p>
 * API reference: https://developers.google.com/google-ads/api/docs/start
 * </p>
 *
 * @deprecated Replaced by the 10-source pipeline (DSRC-04 through DSRC-07).
 *             Use the new wrappers in {@link com.autoresolve.mediabuying.integration.wrapper}
 *             (PytrendsApiWrapper, EbayApiWrapper, etc.) which implement the Spring Events pipeline.
 */
@Deprecated
@Component
public class GoogleAdsApiWrapper extends BaseApiWrapper<PlatformApiResponse> {

    private static final Logger log = LoggerFactory.getLogger(GoogleAdsApiWrapper.class);

    private static final String BASE_URL = "https://googleads.googleapis.com/v17";

    private final String developerToken;
    private final String customerId;
    private final String loginCustomerId;
    private final boolean configured;

    public GoogleAdsApiWrapper(
            @Value("${integration.google-ads.api-key:}") String apiKey,
            @Value("${integration.oauth.google-ads.developer-token:}") String developerToken,
            @Value("${integration.google-ads.customer-id:}") String customerId,
            @Value("${integration.google-ads.login-customer-id:}") String loginCustomerId,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.developerToken = developerToken;
        this.customerId = customerId;
        this.loginCustomerId = loginCustomerId;
        this.configured = (apiKey != null && !apiKey.isEmpty())
                || (developerToken != null && !developerToken.isEmpty());
        if (!this.configured) {
            log.warn("Google Ads API not configured (no API key or developer token); will skip real API calls");
        }
    }

    /**
     * Fetches Google Ads metrics for the given customer.
     *
     * @param customerIdOverride optional override; if empty, uses configured customerId
     * @return normalized PlatformApiResponse, or null if not available
     */
    public PlatformApiResponse fetchMetrics(String customerIdOverride) {
        if (!configured) {
            log.warn("Google Ads API not configured - returning null (no data)");
            return null;
        }

        String effectiveCustomerId = (customerIdOverride != null && !customerIdOverride.isEmpty())
                ? customerIdOverride : customerId;

        return executeWithRetry(() -> {
            // Build GAQL query for KPI metrics
            String gaql = "SELECT "
                    + "campaign.id, campaign.name, "
                    + "metrics.impressions, metrics.clicks, metrics.ctr, "
                    + "metrics.average_cpc, metrics.cost_micros, "
                    + "metrics.conversions, metrics.conversion_rate, "
                    + "metrics.conversions_value "
                    + "FROM campaign "
                    + "WHERE segments.date DURING LAST_7_DAYS "
                    + "ORDER BY metrics.conversions_value DESC";

            HttpHeaders headers = buildHeaders();
            String url = BASE_URL + "/customers/" + effectiveCustomerId + "/googleAds:search";

            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("query", gaql);
            requestBody.put("pageSize", 100);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            if (response.getBody() == null) {
                log.warn("Google Ads API returned null body");
                return null;
            }

            return parseResponse(response.getBody());

        }, "google_ads");
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + tokenManager.getAccessToken("google_ads"));
        headers.set("developer-token", developerToken);
        headers.set("login-customer-id", loginCustomerId);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private PlatformApiResponse parseResponse(Map<String, Object> body) {
        // Extract aggregated metrics from the response
        Object resultsObj = body.get("results");
        if (!(resultsObj instanceof java.util.List) || ((java.util.List<?>) resultsObj).isEmpty()) {
            log.warn("Google Ads API returned no results");
            return null;
        }

        java.util.List<Map<String, Object>> results = (java.util.List<Map<String, Object>>) resultsObj;

        // Aggregate across all campaigns
        long totalImpressions = 0;
        long totalClicks = 0;
        long totalCostMicros = 0;
        double totalConversionsValue = 0;
        long totalConversions = 0;
        double totalConversionRateNumerator = 0;
        long conversionCount = 0;

        for (Map<String, Object> row : results) {
            Map<String, Object> metrics = (Map<String, Object>) row.get("metrics");
            if (metrics == null) continue;

            totalImpressions += getLong(metrics, "impressions");
            totalClicks += getLong(metrics, "clicks");
            totalCostMicros += getLong(metrics, "costMicros");
            totalConversions += getLong(metrics, "conversions");
            totalConversionsValue += getDouble(metrics, "conversionsValue");
        }

        // Calculate derived KPIs
        double costInCurrency = totalCostMicros / 1_000_000.0;
        double averageCpc = totalClicks > 0 ? costInCurrency / totalClicks : 0;
        double conversionRate = totalClicks > 0 ? (double) totalConversions / totalClicks : 0;

        return PlatformApiResponse.builder()
                .roas(totalCostMicros > 0
                        ? BigDecimal.valueOf(totalConversionsValue / costInCurrency)
                        : BigDecimal.ZERO)
                .cac(BigDecimal.valueOf(averageCpc))
                .cltv(BigDecimal.valueOf(totalConversionsValue / Math.max(totalConversions, 1)))
                .conversionRate(BigDecimal.valueOf(conversionRate))
                .scalability(BigDecimal.valueOf(totalImpressions))
                .attributionAccuracy(new BigDecimal("0.85")) // Google Ads last-click default
                .contributionMargin(BigDecimal.valueOf(totalConversionsValue - costInCurrency))
                .paybackPeriod(BigDecimal.valueOf(costInCurrency > 0 ? 30 / Math.max(conversionRate, 0.001) / 30 : 3.0))
                .incrementalReturn(BigDecimal.valueOf(totalConversionsValue * 0.15)) // estimated lift
                .costPerQualifiedLead(totalClicks > 0
                        ? BigDecimal.valueOf(costInCurrency / totalClicks * 10)
                        : BigDecimal.ZERO)
                .cashConversionCycle(new BigDecimal("30"))
                .saturationPoint(new BigDecimal("0.15"))
                .dataSource("Google Ads API v17")
                .build();
    }

    private long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }
}
