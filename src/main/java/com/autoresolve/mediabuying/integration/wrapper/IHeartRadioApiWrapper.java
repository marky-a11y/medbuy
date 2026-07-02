package com.autoresolve.mediabuying.integration.wrapper;

import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.dto.PlatformApiResponse;
import com.autoresolve.mediabuying.integration.ratelimit.AdPlatformRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Wrapper for iHeartMedia (iHeartRadio) advertising API.
 * <p>
 * Since iHeartMedia does not have a fully public ad API, this wrapper
 * returns realistic mock data by default. Set {@code integration.iheart-radio.mock-enabled=false}
 * to attempt a real API call (currently a placeholder for future implementation).
 * </p>
 * API reference: https://www.iheartmedia.com/advertise
 *
 * @deprecated Replaced by the 10-source pipeline (DSRC-04 through DSRC-07).
 *             Use the new wrappers in {@link com.autoresolve.mediabuying.integration.wrapper}
 *             (PytrendsApiWrapper, EbayApiWrapper, etc.) which implement the Spring Events pipeline.
 */
@Deprecated
@Component
public class IHeartRadioApiWrapper extends BaseApiWrapper<PlatformApiResponse> {

    private static final Logger log = LoggerFactory.getLogger(IHeartRadioApiWrapper.class);

    private final String apiKey;
    private final String stationId;
    private final boolean mockEnabled;

    public IHeartRadioApiWrapper(
            @Value("${integration.iheart-radio.api-key:}") String apiKey,
            @Value("${integration.iheart-radio.station-id:station_001}") String stationId,
            @Value("${integration.iheart-radio.mock-enabled:true}") boolean mockEnabled,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.stationId = stationId;
        this.mockEnabled = mockEnabled;
        if (!mockEnabled && (apiKey == null || apiKey.isEmpty())) {
            log.warn("iHeart Radio mock is disabled but no API key configured; will fall back to mock");
        }
    }

    /**
     * Fetches iHeart Radio metrics.
     * <p>
     * When mock is enabled (default), returns realistic estimated data.
     * When mock is disabled and API key is configured, attempts a real API call
     * (currently a placeholder - will return mock data).
     * </p>
     *
     * @param stationIdOverride optional station ID override; if empty, uses configured stationId
     * @return PlatformApiResponse with estimated or real data
     */
    public PlatformApiResponse fetchMetrics(String stationIdOverride) {
        String effectiveStationId = (stationIdOverride != null && !stationIdOverride.isEmpty())
                ? stationIdOverride : this.stationId;

        if (mockEnabled) {
            log.debug("Returning mock iHeart Radio data for station={}", effectiveStationId);
            return buildMockResponse(effectiveStationId);
        }

        // Real API call attempt (placeholder for future implementation)
        if (apiKey != null && !apiKey.isEmpty()) {
            log.warn("iHeart Radio real API call not yet implemented; returning mock data");
            return buildMockResponse(effectiveStationId);
        }

        log.warn("iHeart Radio not configured and mock disabled; returning null");
        return null;
    }

    private PlatformApiResponse buildMockResponse(String stationId) {
        return PlatformApiResponse.builder()
                // Platform info
                .platformName("iheart_radio")
                .sectorName("retail")
                // Core KPIs - realistic estimates for radio advertising
                .roas(new BigDecimal("3.2"))
                .cac(new BigDecimal("35.00"))
                .cltv(new BigDecimal("420.00"))
                .conversionRate(new BigDecimal("0.028"))
                .scalability(new BigDecimal("850000"))
                .attributionAccuracy(new BigDecimal("0.45")) // Radio attribution is less precise
                // Extended KPIs
                .contributionMargin(new BigDecimal("95.00"))
                .paybackPeriod(new BigDecimal("2.8"))
                .incrementalReturn(new BigDecimal("180.00"))
                .costPerQualifiedLead(new BigDecimal("28.50"))
                .cashConversionCycle(new BigDecimal("55"))
                .saturationPoint(new BigDecimal("0.12"))
                // Metadata
                .dataSource("iHeart Radio (estimated)")
                .build();
    }
}
