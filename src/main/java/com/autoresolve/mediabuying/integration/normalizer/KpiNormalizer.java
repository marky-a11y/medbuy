package com.autoresolve.mediabuying.integration.normalizer;

import com.autoresolve.mediabuying.integration.dto.PlatformApiResponse;
import com.autoresolve.mediabuying.messaging.dto.RawKPIEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Normalizes {@link PlatformApiResponse} from ad-platform wrappers into
 * {@link RawKPIEvent} for publishing to the event bus.
 */
@Component
public class KpiNormalizer {

    private static final Logger log = LoggerFactory.getLogger(KpiNormalizer.class);

    /**
     * Mapping from normalized platform names to their corresponding data source names
     * as stored in the {@code data_source} seed table.
     */
    private static final Map<String, String> PLATFORM_TO_SOURCE_NAME;

    static {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("google_ads", "Google Ads API v17");
        mapping.put("meta_ads", "Meta Marketing API");
        mapping.put("tiktok_ads", "TikTok Ads Manager API");
        mapping.put("linkedin_ads", "LinkedIn Marketing API");
        mapping.put("iheart_radio", "iHeart Media (estimated)");
        PLATFORM_TO_SOURCE_NAME = Collections.unmodifiableMap(mapping);
    }

    /**
     * Converts a platform API response into a normalized RawKPIEvent.
     *
     * @param response     the platform API response (may be null)
     * @param platformName the source platform name (e.g. "GOOGLE_ADS")
     * @return a populated RawKPIEvent, or null if response is null
     */
    public RawKPIEvent toRawKpiEvent(PlatformApiResponse response, String platformName) {
        if (response == null) {
            log.warn("Null PlatformApiResponse for platform={}, returning null", platformName);
            return null;
        }

        RawKPIEvent event = new RawKPIEvent();
        event.setEventId(UUID.randomUUID().toString());
        String normalizedPlatformName = normalizePlatformName(platformName);
        event.setPlatformName(normalizedPlatformName);
        event.setSectorName(response.getSectorName());
        event.setDataSource(response.getDataSource());
        event.setIngestionTimestamp(Instant.now());

        // Populate source references from the platform name mapping
        List<String> sourceRefs = resolveSourceReferences(normalizedPlatformName);
        event.setSourceReferences(sourceRefs);

        // Core KPIs (6 inputs to composite scoring)
        event.setRoas(bigDecimalToDouble(response.getRoas()));
        event.setCac(bigDecimalToDouble(response.getCac()));
        event.setCltv(bigDecimalToDouble(response.getCltv()));
        event.setConversionRate(bigDecimalToDouble(response.getConversionRate()));
        event.setScalability(bigDecimalToDouble(response.getScalability()));
        event.setAttributionAccuracy(bigDecimalToDouble(response.getAttributionAccuracy()));

        // Extended KPIs
        event.setContributionMargin(bigDecimalToDouble(response.getContributionMargin()));
        event.setPaybackPeriod(bigDecimalToDouble(response.getPaybackPeriod()));
        event.setIncrementalReturn(bigDecimalToDouble(response.getIncrementalReturn()));
        event.setCpql(bigDecimalToDouble(response.getCostPerQualifiedLead()));
        event.setCashConversionCycle(bigDecimalToDouble(response.getCashConversionCycle()));
        event.setSaturationPoint(bigDecimalToDouble(response.getSaturationPoint()));

        log.debug("Normalized KPI event: id={}, platform={}, sector={}, sources={}",
                event.getEventId(), event.getPlatformName(), event.getSectorName(), sourceRefs);

        return event;
    }

    /**
     * Resolves the data source names for a given normalized platform name.
     *
     * @param normalizedPlatformName the normalized platform name (e.g. "google_ads")
     * @return a singleton list with the mapped source name, or an empty list if unknown
     */
    public List<String> resolveSourceReferences(String normalizedPlatformName) {
        String sourceName = PLATFORM_TO_SOURCE_NAME.get(normalizedPlatformName);
        if (sourceName != null) {
            return Collections.singletonList(sourceName);
        }
        log.warn("No source name mapping found for platform '{}'", normalizedPlatformName);
        return Collections.emptyList();
    }

    /**
     * Normalizes a platform name to lowercase with underscores.
     * e.g. "GOOGLE_ADS" -> "google_ads", "Meta Ads" -> "meta_ads"
     */
    public String normalizePlatformName(String rawName) {
        if (rawName == null) {
            return null;
        }
        return rawName.trim()
                .toLowerCase()
                .replaceAll("\\s+", "_")
                .replaceAll("-", "_");
    }

    /**
     * Null-safe BigDecimal to Double conversion.
     */
    private Double bigDecimalToDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
