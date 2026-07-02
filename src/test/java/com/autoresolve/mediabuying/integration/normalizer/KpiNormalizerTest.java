package com.autoresolve.mediabuying.integration.normalizer;

import com.autoresolve.mediabuying.integration.dto.PlatformApiResponse;
import com.autoresolve.mediabuying.messaging.dto.RawKPIEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class KpiNormalizerTest {

    private KpiNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new KpiNormalizer();
    }

    @Test
    void testAllKpiFieldsMappedCorrectly() {
        PlatformApiResponse response = PlatformApiResponse.builder()
                .sectorName("technology")
                .dataSource("Google Ads API v17")
                .roas(new BigDecimal("4.5"))
                .cac(new BigDecimal("23.50"))
                .cltv(new BigDecimal("487.00"))
                .conversionRate(new BigDecimal("0.0345"))
                .scalability(new BigDecimal("250000"))
                .attributionAccuracy(new BigDecimal("0.91"))
                .contributionMargin(new BigDecimal("112.40"))
                .paybackPeriod(new BigDecimal("1.9"))
                .incrementalReturn(new BigDecimal("145.80"))
                .costPerQualifiedLead(new BigDecimal("18.75"))
                .cashConversionCycle(new BigDecimal("45"))
                .saturationPoint(new BigDecimal("0.32"))
                .build();

        RawKPIEvent event = normalizer.toRawKpiEvent(response, "google_ads");

        assertNotNull(event);
        assertNotNull(event.getEventId());
        assertEquals("google_ads", event.getPlatformName());
        assertEquals("technology", event.getSectorName());
        assertEquals("Google Ads API v17", event.getDataSource());
        assertNotNull(event.getIngestionTimestamp());

        // Core KPIs
        assertEquals(4.5, event.getRoas(), 0.001);
        assertEquals(23.50, event.getCac(), 0.001);
        assertEquals(487.00, event.getCltv(), 0.001);
        assertEquals(0.0345, event.getConversionRate(), 0.0001);
        assertEquals(250000.0, event.getScalability(), 0.001);
        assertEquals(0.91, event.getAttributionAccuracy(), 0.001);

        // Extended KPIs
        assertEquals(112.40, event.getContributionMargin(), 0.001);
        assertEquals(1.9, event.getPaybackPeriod(), 0.001);
        assertEquals(145.80, event.getIncrementalReturn(), 0.001);
        assertEquals(18.75, event.getCpql(), 0.001);
        assertEquals(45.0, event.getCashConversionCycle(), 0.001);
        assertEquals(0.32, event.getSaturationPoint(), 0.001);
    }

    @Test
    void testNullFieldsProduceNullInRawKPIEvent() {
        PlatformApiResponse response = PlatformApiResponse.builder()
                .sectorName("technology")
                .dataSource("Test API")
                .roas(null)
                .cac(null)
                .cltv(null)
                .conversionRate(null)
                .scalability(null)
                .attributionAccuracy(null)
                .contributionMargin(null)
                .paybackPeriod(null)
                .incrementalReturn(null)
                .costPerQualifiedLead(null)
                .cashConversionCycle(null)
                .saturationPoint(null)
                .build();

        RawKPIEvent event = normalizer.toRawKpiEvent(response, "test_platform");

        assertNotNull(event);
        assertNull(event.getRoas());
        assertNull(event.getCac());
        assertNull(event.getCltv());
        assertNull(event.getConversionRate());
        assertNull(event.getScalability());
        assertNull(event.getAttributionAccuracy());
        assertNull(event.getContributionMargin());
        assertNull(event.getPaybackPeriod());
        assertNull(event.getIncrementalReturn());
        assertNull(event.getCpql());
        assertNull(event.getCashConversionCycle());
        assertNull(event.getSaturationPoint());
    }

    @Test
    void testNullResponseReturnsNull() {
        RawKPIEvent event = normalizer.toRawKpiEvent(null, "test_platform");
        assertNull(event);
    }

    @Test
    void testPlatformNameNormalization() {
        assertEquals("google_ads", normalizer.normalizePlatformName("GOOGLE_ADS"));
        assertEquals("meta_ads", normalizer.normalizePlatformName("Meta Ads"));
        assertEquals("tiktok_ads", normalizer.normalizePlatformName("TikTok Ads"));
        assertEquals("linkedin_ads", normalizer.normalizePlatformName("LinkedIn Ads"));
        assertEquals("iheart_radio", normalizer.normalizePlatformName("iHeart Radio"));
        assertEquals("my_custom_platform", normalizer.normalizePlatformName("  My Custom Platform  "));
        assertNull(normalizer.normalizePlatformName(null));
    }

    @Test
    void testEventIdIsUuid() {
        PlatformApiResponse response = PlatformApiResponse.builder()
                .sectorName("technology")
                .dataSource("Test")
                .roas(BigDecimal.ONE)
                .build();

        RawKPIEvent event = normalizer.toRawKpiEvent(response, "platform");
        assertNotNull(event.getEventId());
        assertDoesNotThrow(() -> java.util.UUID.fromString(event.getEventId()));
    }

    @Test
    void testIngestionTimestampIsRecent() {
        PlatformApiResponse response = PlatformApiResponse.builder()
                .sectorName("technology")
                .dataSource("Test")
                .roas(BigDecimal.ONE)
                .build();

        RawKPIEvent event = normalizer.toRawKpiEvent(response, "platform");
        assertNotNull(event.getIngestionTimestamp());
        // Should be within the last 5 seconds
        assertTrue(java.time.Duration.between(
                event.getIngestionTimestamp(), java.time.Instant.now()).getSeconds() < 5);
    }
}
