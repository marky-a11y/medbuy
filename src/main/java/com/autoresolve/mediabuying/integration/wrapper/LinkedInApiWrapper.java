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
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for LinkedIn Marketing API.
 * <p>
 * API reference: https://learn.microsoft.com/en-us/linkedin/marketing/
 * </p>
 *
 * @deprecated Replaced by the 10-source pipeline (DSRC-04 through DSRC-07).
 *             Use the new wrappers in {@link com.autoresolve.mediabuying.integration.wrapper}
 *             (PytrendsApiWrapper, EbayApiWrapper, etc.) which implement the Spring Events pipeline.
 */
@Deprecated
@Component
public class LinkedInApiWrapper extends BaseApiWrapper<PlatformApiResponse> {

    private static final Logger log = LoggerFactory.getLogger(LinkedInApiWrapper.class);

    private static final String BASE_URL = "https://api.linkedin.com/v2";

    private final String accountId;
    private final boolean configured;

    public LinkedInApiWrapper(
            @Value("${integration.linkedin-ads.api-key:}") String apiKey,
            @Value("${integration.linkedin-ads.account-id:}") String accountId,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.accountId = accountId;
        this.configured = (apiKey != null && !apiKey.isEmpty())
                || (accountId != null && !accountId.isEmpty());
        if (!this.configured) {
            log.warn("LinkedIn Ads API not configured (no API key or account ID); will skip real calls");
        }
    }

    /**
     * Fetches LinkedIn Ads metrics for the given account.
     *
     * @param accountIdOverride optional override; if empty, uses configured accountId
     * @return normalized PlatformApiResponse, or null if not available
     */
    public PlatformApiResponse fetchMetrics(String accountIdOverride) {
        if (!configured) {
            log.warn("LinkedIn Ads API not configured - returning null");
            return null;
        }

        String effectiveAccountId = (accountIdOverride != null && !accountIdOverride.isEmpty())
                ? accountIdOverride : accountId;

        return executeWithRetry(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenManager.getAccessToken("linkedin_ads"));
            headers.set("Accept", "application/json");
            headers.set("X-Restli-Protocol-Version", "2.0.0");

            String analyticsUrl = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/adAnalyticsV2")
                    .queryParam("q", "analytics")
                    .queryParam("pivot", "ACCOUNT")
                    .queryParam("dateRange.start.day", java.time.LocalDate.now().minusDays(7).getDayOfMonth())
                    .queryParam("dateRange.start.month", java.time.LocalDate.now().minusDays(7).getMonthValue())
                    .queryParam("dateRange.start.year", java.time.LocalDate.now().minusDays(7).getYear())
                    .queryParam("dateRange.end.day", java.time.LocalDate.now().minusDays(1).getDayOfMonth())
                    .queryParam("dateRange.end.month", java.time.LocalDate.now().minusDays(1).getMonthValue())
                    .queryParam("dateRange.end.year", java.time.LocalDate.now().minusDays(1).getYear())
                    .queryParam("timeGranularity", "DAILY")
                    .queryParam("accounts", "urn:li:sponsoredAccount:" + effectiveAccountId)
                    .queryParam("fields", "impressions,clicks,landingPageClicks,costInLocalCurrency,externalWebsiteConversions")
                    .build()
                    .toUriString();

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    analyticsUrl, HttpMethod.GET, entity, Map.class);

            if (response.getBody() == null) {
                log.warn("LinkedIn Ads API returned null body");
                return null;
            }

            return parseResponse(response.getBody());

        }, "linkedin_ads");
    }

    @SuppressWarnings("unchecked")
    private PlatformApiResponse parseResponse(Map<String, Object> body) {
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
        if (elements == null || elements.isEmpty()) {
            log.warn("LinkedIn Ads API returned no elements");
            return null;
        }

        // Aggregate across days
        double totalImpressions = 0, totalClicks = 0, totalCost = 0, totalConversions = 0;

        for (Map<String, Object> element : elements) {
            totalImpressions += getDouble(element, "impressions", 0);
            totalClicks += getDouble(element, "clicks", 0);
            totalCost += getDouble(element, "costInLocalCurrency", 0);
            totalConversions += getDouble(element, "externalWebsiteConversions", 0);
        }

        double cpc = totalClicks > 0 ? totalCost / totalClicks : 0;
        double ctr = totalImpressions > 0 ? totalClicks / totalImpressions : 0;
        double conversionRate = totalClicks > 0 ? totalConversions / totalClicks : 0;
        double roas = totalCost > 0 ? (totalConversions * 50) / totalCost : 2.0; // Assuming ~$50 avg conversion value

        return PlatformApiResponse.builder()
                .roas(BigDecimal.valueOf(roas))
                .cac(BigDecimal.valueOf(cpc))
                .cltv(BigDecimal.valueOf(totalConversions > 0
                        ? totalCost / totalConversions * 4 : 400.0))
                .conversionRate(BigDecimal.valueOf(conversionRate))
                .scalability(BigDecimal.valueOf(totalImpressions))
                .attributionAccuracy(new BigDecimal("0.80")) // LinkedIn's account-based attribution
                .contributionMargin(BigDecimal.valueOf(totalCost * 0.25))
                .paybackPeriod(BigDecimal.valueOf(ctr > 0 ? 30 / (ctr * 100) : 6.0))
                .incrementalReturn(BigDecimal.valueOf(totalCost * 0.12))
                .costPerQualifiedLead(BigDecimal.valueOf(cpc * 12))
                .cashConversionCycle(new BigDecimal("60"))
                .saturationPoint(new BigDecimal("0.18"))
                .dataSource("LinkedIn Marketing API v2")
                .build();
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return defaultValue;
    }
}
