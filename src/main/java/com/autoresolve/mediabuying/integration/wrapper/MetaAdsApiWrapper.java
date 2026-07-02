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
 * Wrapper for Meta (Facebook) Marketing API.
 * <p>
 * API reference: https://developers.facebook.com/docs/marketing-apis/
 * </p>
 *
 * @deprecated Replaced by the 10-source pipeline (DSRC-04 through DSRC-07).
 *             Use the new wrappers in {@link com.autoresolve.mediabuying.integration.wrapper}
 *             (PytrendsApiWrapper, EbayApiWrapper, etc.) which implement the Spring Events pipeline.
 */
@Deprecated
@Component
public class MetaAdsApiWrapper extends BaseApiWrapper<PlatformApiResponse> {

    private static final Logger log = LoggerFactory.getLogger(MetaAdsApiWrapper.class);

    private static final String BASE_URL = "https://graph.facebook.com/v18.0";

    private final String adAccountId;
    private final String appId;
    private final String appSecret;
    private final boolean configured;

    public MetaAdsApiWrapper(
            @Value("${integration.meta-ads.api-key:}") String apiKey,
            @Value("${integration.meta-ads.ad-account-id:}") String adAccountId,
            @Value("${integration.meta-ads.app-id:}") String appId,
            @Value("${integration.meta-ads.app-secret:}") String appSecret,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.adAccountId = adAccountId;
        this.appId = appId;
        this.appSecret = appSecret;
        this.configured = (apiKey != null && !apiKey.isEmpty())
                || (adAccountId != null && !adAccountId.isEmpty());
        if (!this.configured) {
            log.warn("Meta Ads API not configured (no API key or ad account ID); will skip real calls");
        }
    }

    /**
     * Fetches Meta Ads metrics for the given ad account.
     *
     * @param adAccountIdOverride optional override; if empty, uses configured adAccountId
     * @return normalized PlatformApiResponse, or null if not available
     */
    public PlatformApiResponse fetchMetrics(String adAccountIdOverride) {
        if (!configured) {
            log.warn("Meta Ads API not configured - returning null");
            return null;
        }

        String effectiveAccountId = (adAccountIdOverride != null && !adAccountIdOverride.isEmpty())
                ? adAccountIdOverride : adAccountId;

        return executeWithRetry(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenManager.getAccessToken("meta_ads"));
            headers.set("Accept", "application/json");

            String insightsUrl = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/act_" + effectiveAccountId + "/insights")
                    .queryParam("fields", "spend,impressions,clicks,ctr,cpc,cpm,reach,actions,conversions,conversion_rate_ranking")
                    .queryParam("date_preset", "last_7d")
                    .queryParam("level", "account")
                    .queryParam("limit", "1")
                    .build()
                    .toUriString();

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    insightsUrl, HttpMethod.GET, entity, Map.class);

            if (response.getBody() == null) {
                log.warn("Meta Ads API returned null body");
                return null;
            }

            return parseResponse(response.getBody());

        }, "meta_ads");
    }

    @SuppressWarnings("unchecked")
    private PlatformApiResponse parseResponse(Map<String, Object> body) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        if (data == null || data.isEmpty()) {
            log.warn("Meta Ads API returned no data");
            return null;
        }

        Map<String, Object> metrics = data.get(0);

        double spend = getDouble(metrics, "spend", 0.0);
        double impressions = getDouble(metrics, "impressions", 0.0);
        double clicks = getDouble(metrics, "clicks", 0.0);
        double ctr = getDouble(metrics, "ctr", 0.0);
        double cpc = getDouble(metrics, "cpc", 0.0);
        double conversions = getDouble(metrics, "conversions", 0.0);

        // Parse actions for conversion breakdown
        double totalConversions = conversions;
        Object actionsObj = metrics.get("actions");
        if (actionsObj instanceof List) {
            List<Map<String, Object>> actions = (List<Map<String, Object>>) actionsObj;
            for (Map<String, Object> action : actions) {
                String actionType = (String) action.get("action_type");
                if ("purchase".equals(actionType)) {
                    totalConversions = getDouble(action, "value", 0.0);
                }
            }
        }

        // Calculate conversion rate
        double conversionRate = impressions > 0 ? totalConversions / impressions : 0;

        // ROAS estimation (if conversion value not available, estimate)
        double roas = spend > 0 ? getDouble(metrics, "conversion_value_ranking", 0.0) / spend * 100 : 0;
        if (roas == 0) {
            roas = cpc > 0 ? 1.0 / cpc : 2.5; // industry-average fallback
        }

        return PlatformApiResponse.builder()
                .roas(BigDecimal.valueOf(roas))
                .cac(BigDecimal.valueOf(cpc))
                .cltv(BigDecimal.valueOf(spend > 0 && totalConversions > 0
                        ? spend / totalConversions * 3 : 250.0))
                .conversionRate(BigDecimal.valueOf(conversionRate))
                .scalability(BigDecimal.valueOf(impressions))
                .attributionAccuracy(new BigDecimal("0.78")) // Meta's mixed attribution
                .contributionMargin(BigDecimal.valueOf(spend * 1.2 - spend))
                .paybackPeriod(BigDecimal.valueOf(ctr > 0 ? 30 / (ctr * 100) : 4.0))
                .incrementalReturn(BigDecimal.valueOf(roas * spend * 0.1))
                .costPerQualifiedLead(BigDecimal.valueOf(cpc * 5))
                .cashConversionCycle(new BigDecimal("45"))
                .saturationPoint(new BigDecimal("0.20"))
                .dataSource("Meta Marketing API v18.0")
                .build();
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return defaultValue;
    }
}
